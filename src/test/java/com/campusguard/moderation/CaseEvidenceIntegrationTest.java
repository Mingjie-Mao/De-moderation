package com.campusguard.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.common.TargetType;
import com.campusguard.evaluation.corpus.DecisionCorpusExporter;
import com.campusguard.media.MediaObjectRepository;
import com.campusguard.moderation.admin.CaseDecisionRequest;
import com.campusguard.post.CreatePostRequest;
import com.campusguard.post.UpdatePostRequest;
import com.campusguard.report.CreateReportRequest;
import com.campusguard.report.ReportReason;
import com.campusguard.user.User;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class CaseEvidenceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private ModerationCaseRepository cases;
    @Autowired private ModerationWorker worker;
    @Autowired private MediaObjectRepository media;
    @Autowired private DecisionCorpusExporter corpus;

    @Test
    void reportedVersionAndAttachmentSurviveAnAuthorEdit() throws Exception {
        User author = newUser();
        User admin = newAdmin();
        ByteArrayOutputStream image = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", image);
        String upload = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "evidence.png", "image/png", image.toByteArray()))
                        .header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID mediaId = UUID.fromString(JsonPath.read(upload, "$.id"));

        String created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreatePostRequest(
                                uniqueForumKey(), "Original title", "You are an idiot", mediaId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID postId = UUID.fromString(JsonPath.read(created, "$.id"));
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", bearer(newUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateReportRequest(TargetType.POST, postId, ReportReason.ABUSE))))
                .andExpect(status().isCreated());
        UUID caseId = cases.findOpenByTarget(TargetType.POST, postId).orElseThrow().getId();
        mockMvc.perform(get("/api/media/{id}", mediaId))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(patch("/api/posts/{id}", postId)
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UpdatePostRequest("Edited title", "Ordinary student content", null))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/media/{id}", mediaId)).andExpect(status().isNotFound());

        worker.runOnce();
        assertThat(cases.findById(caseId).orElseThrow().getDecision())
                .isEqualTo(ModerationDecision.REMOVE);

        // Queue cards must show the reported version, even after the author edits it.
        var queueEntry = reviewQueueEntry(admin, CaseStatus.AWAITING_REVIEW, caseId);
        assertThat(queueEntry.path("contentTitle").asText()).isEqualTo("Original title");
        assertThat(queueEntry.path("contentPreview").asText()).isEqualTo("You are an idiot");
        assertThat(queueEntry.path("hasAttachment").asBoolean()).isTrue();

        String detail = mockMvc.perform(get("/api/admin/moderation-cases/{id}", caseId)
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.body").value("You are an idiot"))
                .andExpect(jsonPath("$.currentContent.body").value("Ordinary student content"))
                .andExpect(jsonPath("$.contentChanged").value(true))
                .andReturn().getResponse().getContentAsString();
        String evidenceUrl = JsonPath.read(detail, "$.content.mediaUrl");
        mockMvc.perform(get(evidenceUrl)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(evidenceUrl).header("Authorization", bearer(author)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(evidenceUrl).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(get("/api/admin/moderation-cases/{caseId}/media/{id}", caseId, UUID.randomUUID())
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
        assertThat(media.findUnreferencedBefore(Instant.now().plusSeconds(60), PageRequest.of(0, 100)))
                .noneMatch(item -> item.getId().equals(mediaId));

        mockMvc.perform(post("/api/admin/moderation-cases/{id}/decision", caseId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CaseDecisionRequest(FinalAction.HIDE, "Original reported wording"))))
                .andExpect(status().isOk());
        assertThat(corpus.export(10000).stream()
                .filter(item -> item.caseId().equals(caseId.toString()))
                .findFirst().orElseThrow().body()).isEqualTo("You are an idiot");
    }
}
