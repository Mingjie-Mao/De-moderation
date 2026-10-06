package com.campusguard.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.common.TargetType;
import com.campusguard.post.CreatePostRequest;
import com.campusguard.report.CreateReportRequest;
import com.campusguard.report.ReportReason;
import com.campusguard.report.ReportService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@TestPropertySource(properties = {
        "campusguard.moderation.scheduler-enabled=true",
        "campusguard.moderation.poll-interval=1h"
})
class ModerationDispatchIntegrationTest extends AbstractIntegrationTest {
    @MockitoBean ModerationWorker worker;
    @Autowired ReportService reports;
    @Autowired ModerationCaseRepository cases;
    @Autowired ModerationWorkerScheduler scheduler;
    @Autowired ModerationProperties properties;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void committedReportWakesWorkerWithoutWaitingForRecoveryPoll() throws Exception {
        UUID postId = createPost();
        var reporter = newUser();
        AtomicBoolean committedCaseVisible = new AtomicBoolean();
        when(worker.runOnce()).thenAnswer(invocation -> {
            committedCaseVisible.set(cases.findOpenCaseId(TargetType.POST, postId).isPresent());
            return 0;
        });
        reports.create(reporter.getId(), new CreateReportRequest(TargetType.POST, postId, ReportReason.OTHER));
        verify(worker, timeout(5000)).runOnce();
        assertThat(committedCaseVisible).isTrue();
    }

    @Test
    void rolledBackReportDoesNotWakeWorkerOrLeaveACase() throws Exception {
        UUID postId = createPost();
        var reporter = newUser();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            reports.create(reporter.getId(), new CreateReportRequest(TargetType.POST, postId, ReportReason.OTHER));
            tx.setRollbackOnly();
        });
        verify(worker, after(300).never()).runOnce();
        assertThat(cases.findOpenCaseId(TargetType.POST, postId)).isEmpty();
    }

    @Test
    void recoveryPollDrainsMultipleFullBatchesWithoutAnotherTimerTick() {
        when(worker.runOnce()).thenReturn(properties.batchSize(), properties.batchSize(), 0);
        scheduler.poll();
        verify(worker, timeout(5000).times(3)).runOnce();
    }

    private UUID createPost() throws Exception {
        var author = newUser();
        String body = mockMvc.perform(post("/api/posts")
                .header("Authorization", bearer(author))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreatePostRequest("anu", "Queue dispatch test", "Harmless study discussion."))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
