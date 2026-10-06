package com.campusguard.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.common.TargetType;
import com.campusguard.media.MediaObject;
import com.campusguard.media.MediaObjectRepository;
import com.campusguard.media.MediaStorage;
import com.campusguard.post.CreatePostRequest;
import com.campusguard.report.CreateReportRequest;
import com.campusguard.report.ReportReason;
import com.campusguard.user.User;
import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class MissingMediaReviewIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MediaObjectRepository media;
    @Autowired private MediaStorage storage;
    @Autowired private ModerationCaseRepository cases;
    @Autowired private ModerationWorker worker;

    @Test
    void missingAttachmentStillReachesHumanReview() throws Exception {
        User author = newUser();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        String upload = mockMvc.perform(multipart("/api/media")
                        .file(new MockMultipartFile("file", "evidence.png", "image/png", bytes.toByteArray()))
                        .header("Authorization", bearer(author)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID mediaId = UUID.fromString(JsonPath.read(upload, "$.id"));
        String created = mockMvc.perform(post("/api/posts")
                        .header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreatePostRequest(uniqueForumKey(), "Image report", "Review this", mediaId))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID postId = UUID.fromString(JsonPath.read(created, "$.id"));
        mockMvc.perform(post("/api/reports")
                        .header("Authorization", bearer(newUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateReportRequest(TargetType.POST, postId, ReportReason.ABUSE))))
                .andExpect(status().isCreated());

        MediaObject object = media.findById(mediaId).orElseThrow();
        storage.delete(object.getStorageKey());
        worker.runOnce();

        ModerationCase moderationCase = cases.findOpenByTarget(TargetType.POST, postId).orElseThrow();
        assertThat(moderationCase.getStatus()).isEqualTo(CaseStatus.AWAITING_REVIEW);
        assertThat(moderationCase.getDecision()).isEqualTo(ModerationDecision.ESCALATE);
        assertThat(moderationCase.getRationale()).contains("attachment is unavailable");
    }
}
