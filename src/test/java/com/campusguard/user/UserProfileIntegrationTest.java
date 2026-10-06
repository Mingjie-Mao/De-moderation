package com.campusguard.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusguard.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class UserProfileIntegrationTest extends AbstractIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired
    com.campusguard.media.MediaObjectRepository repository;

    @Test
    void patchingOneProfileFieldDoesNotEraseTheOther() throws Exception {
        User user = newUser();

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdateProfileRequest("First name", "Existing bio"))))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/users/me")
                        .header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "Changed name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Changed name"))
                .andExpect(jsonPath("$.bio").value("Existing bio"));

        mockMvc.perform(get("/api/users/{id}", user.getId())
                        .header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Changed name"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void preferencesSurviveFreshReadsAndPartialPatchesWithoutLeakingToOtherUsers() throws Exception {
        User user = newUser();
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("languageTag", "zh-CN", "theme", "dark", "avatarColor", 4))))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("displayName", "New name"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.languageTag").value("zh-CN"))
                .andExpect(jsonPath("$.theme").value("dark"));
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(user)))
                .andExpect(jsonPath("$.avatarColor").value(4));
        mockMvc.perform(get("/api/users/{id}", user.getId()).header("Authorization", bearer(newUser())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarColor").value(4))
                .andExpect(jsonPath("$.languageTag").doesNotExist()).andExpect(jsonPath("$.theme").doesNotExist());
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("languageTag", "invalid"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("avatarColor", 10))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAnOwnedAvatarBecomesPublicAndSweepingRetainsItUntilRemoved() throws Exception {
        User owner = newUser(), other = newUser();
        var image = new java.awt.image.BufferedImage(4, 3, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var out = new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image, "png", out);
        String upload = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/media")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "avatar.png", "image/png", out.toByteArray()))
                .header("Authorization", bearer(owner))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(upload, "$.id");
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(other))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("avatarMediaId", id))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("avatarMediaId", id))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value("/api/media/" + id + "?v=2"));
        mockMvc.perform(get("/api/media/{id}", id)).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(repository.findUnreferencedBefore(java.time.Instant.now().plusSeconds(60),
                org.springframework.data.domain.PageRequest.of(0, 1000))).noneMatch(m -> m.getId().toString().equals(id));
        mockMvc.perform(patch("/api/users/me").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("removeAvatar", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
        mockMvc.perform(get("/api/media/{id}", id)).andExpect(status().isNotFound());
    }
}
