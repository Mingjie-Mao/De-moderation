package com.campusguard.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusguard.AbstractIntegrationTest;
import com.campusguard.comment.CommentService;
import com.campusguard.comment.CreateCommentRequest;
import com.campusguard.post.CreatePostRequest;
import com.campusguard.post.PostService;
import com.campusguard.post.UpdatePostRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.TestPropertySource;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;

@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=1")
class TranslationApiIntegrationTest extends AbstractIntegrationTest {

    @TestConfiguration
    static class StubTranslatorConfig {
        static final StubTranslator MODEL = new StubTranslator();

        @Bean
        Translator stubTranslator() {
            return MODEL;
        }
    }

    /** Prefixes every text, records what it was sent, and can be switched off. */
    static final class StubTranslator implements Translator {
        final List<Map<String, String>> calls = new CopyOnWriteArrayList<>();
        volatile boolean down;
        volatile CountDownLatch started, release;

        @Override
        public String modelName() {
            return "stub-translator";
        }

        @Override
        public Map<String, String> translate(Map<String, String> texts, String language) {
            calls.add(texts);
            if (started != null) started.countDown();
            if (release != null) {
                try { release.await(15, TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
            }
            if (down) throw new IllegalStateException("provider unavailable");
            Map<String, String> answer = new LinkedHashMap<>();
            texts.forEach((key, text) -> answer.put(key, "译：" + text));
            return answer;
        }
    }

    @Autowired com.campusguard.report.ReportService reports;
    @Autowired com.campusguard.moderation.ModerationCaseRepository cases;
    @Autowired PostService posts;
    @Autowired CommentService comments;
    @Autowired @Qualifier("translationExecutor") ExecutorService executor;

    @BeforeEach
    void reset() {
        StubTranslatorConfig.MODEL.calls.clear();
        StubTranslatorConfig.MODEL.down = false;
        StubTranslatorConfig.MODEL.started = null;
        StubTranslatorConfig.MODEL.release = null;
    }

    @AfterEach void drain() {
        if (StubTranslatorConfig.MODEL.release != null) StubTranslatorConfig.MODEL.release.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() ->
                ((ThreadPoolExecutor)executor).getActiveCount() == 0 && ((ThreadPoolExecutor)executor).getQueue().isEmpty());
    }

    @Test
    void administratorTranslatesReportedVersionsAfterEditsAndDeletion() throws Exception {
        var author = newUser(); var admin = newAdmin();
        var post = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "Reported title", "Reported body"));
        var comment = comments.create(post.id(), author.getId(), new CreateCommentRequest(null, "Reported comment"));
        reports.create(newUser().getId(), new com.campusguard.report.CreateReportRequest(
                com.campusguard.common.TargetType.POST, post.id(), com.campusguard.report.ReportReason.ABUSE));
        reports.create(newUser().getId(), new com.campusguard.report.CreateReportRequest(
                com.campusguard.common.TargetType.COMMENT, comment.id(), com.campusguard.report.ReportReason.ABUSE));
        UUID postCase = cases.findOpenByTarget(com.campusguard.common.TargetType.POST, post.id()).orElseThrow().getId();
        UUID commentCase = cases.findOpenByTarget(com.campusguard.common.TargetType.COMMENT, comment.id()).orElseThrow().getId();
        posts.update(author.getId(), post.id(), new UpdatePostRequest("Edited title", "Edited body", null));
        posts.delete(author.getId(), post.id());
        var request = new AdminTranslationController.Request("zh-CN", List.of(postCase, commentCase));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> mockMvc.perform(post("/api/admin/translations")
                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pending").value(false))
                .andExpect(jsonPath("$.cases[?(@.id == '"+postCase+"')].title").value(org.hamcrest.Matchers.hasItem("译：Reported title")))
                .andExpect(jsonPath("$.cases[?(@.id == '"+postCase+"')].sourcePreview").value(org.hamcrest.Matchers.hasItem("Reported body")))
                .andExpect(jsonPath("$.cases[?(@.id == '"+commentCase+"')].body").value(org.hamcrest.Matchers.hasItem("译：Reported comment"))));
        mockMvc.perform(post("/api/admin/translations").header("Authorization", bearer(newAdmin()))
                .contentType(MediaType.APPLICATION_JSON).content(json(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cases.length()").value(2));
        assertThat(StubTranslatorConfig.MODEL.calls).hasSize(1);
        assertThat(StubTranslatorConfig.MODEL.calls.getFirst().values()).doesNotContain("Edited title", "Edited body");
    }

    @Test
    void evidenceTranslationIsAdminOnlyAndBounded() throws Exception {
        String body = json(new AdminTranslationController.Request("zh-CN", List.of(UUID.randomUUID())));
        mockMvc.perform(post("/api/admin/translations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/translations").header("Authorization", bearer(newUser()))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/translations").header("Authorization", bearer(newAdmin()))
                .contentType(MediaType.APPLICATION_JSON).content(json(new AdminTranslationController.Request("zh-CN",
                    java.util.stream.IntStream.range(0,31).mapToObj(i -> UUID.randomUUID()).toList()))))
                .andExpect(status().isBadRequest());
        assertThat(StubTranslatorConfig.MODEL.calls).isEmpty();
    }

    @Test
    void translatesOnceAndServesEveryLaterReaderFromCache() throws Exception {
        var author = newUser();
        var post = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "Exam tips", "Start early."));
        var comment = comments.create(post.id(), author.getId(), new CreateCommentRequest(null, "Good luck"));

        for (var reader : List.of(newUser(), newUser())) {
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> translate(reader, List.of(post.id()), List.of(comment.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.posts[0].title").value("译：Exam tips"))
                    .andExpect(jsonPath("$.posts[0].body").value("译：Start early."))
                    .andExpect(jsonPath("$.comments[0].body").value("译：Good luck"))
                    .andExpect(jsonPath("$.pending").value(false)));
        }
        assertThat(StubTranslatorConfig.MODEL.calls).hasSize(1);
    }

    @Test
    void chineseTextIsLeftAloneAndEditsAreTranslatedAgain() throws Exception {
        var author = newUser();
        var reader = newUser();
        var chinese = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "COMP2100 期末怎么复习", "求经验"));
        var english = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "Old title", "Old body"));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> translate(reader, List.of(chinese.id(), english.id()), List.of())
                .andExpect(jsonPath("$.posts.length()").value(1))
                .andExpect(jsonPath("$.posts[0].id").value(english.id().toString())));

        posts.update(author.getId(), english.id(), new UpdatePostRequest("New title", "New body", null, null));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> translate(reader, List.of(english.id()), List.of())
                .andExpect(jsonPath("$.posts[0].title").value("译：New title")));
        assertThat(StubTranslatorConfig.MODEL.calls).hasSize(2);
        assertThat(StubTranslatorConfig.MODEL.calls.getFirst().values()).noneMatch(text -> text.contains("期末"));
    }

    @Test
    void removedContentIsNeverTranslatedAndOutagesLeaveTheOriginal() throws Exception {
        var author = newUser();
        var reader = newUser();
        var removed = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "Removed", "Gone"));
        posts.delete(author.getId(), removed.id());
        translate(reader, List.of(removed.id()), List.of())
                .andExpect(jsonPath("$.posts").isEmpty())
                .andExpect(jsonPath("$.pending").value(false));

        var live = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "Live", "Here"));
        StubTranslatorConfig.MODEL.down = true;
        translate(reader, List.of(live.id()), List.of())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posts").isEmpty())
                .andExpect(jsonPath("$.pending").value(true));
        drain();
        StubTranslatorConfig.MODEL.down = false;
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> translate(reader, List.of(live.id()), List.of())
                .andExpect(jsonPath("$.posts[0].title").value("译：Live")));
    }

    @Test void blockedModelCannotHoldTheHttpRequestOrTheOnlyDatabaseConnection() throws Exception {
        var author = newUser(); var reader = newUser(); var other = newUser();
        var post = posts.create(author.getId(), new CreatePostRequest(uniqueForumKey(), "Slow model", "Keep reading the original."));
        StubTranslatorConfig.MODEL.started = new CountDownLatch(1);
        StubTranslatorConfig.MODEL.release = new CountDownLatch(1);
        long start = System.nanoTime();
        translate(reader, List.of(post.id()), List.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true));
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));
        assertThat(StubTranslatorConfig.MODEL.started.await(2, TimeUnit.SECONDS)).isTrue();
        translate(other, List.of(post.id()), List.of()).andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(true));
        start = System.nanoTime();
        mockMvc.perform(get("/api/users/me").header("Authorization", bearer(reader))).andExpect(status().isOk());
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(2));
        assertThat(StubTranslatorConfig.MODEL.calls).hasSize(1);
        StubTranslatorConfig.MODEL.release.countDown();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> translate(reader, List.of(post.id()), List.of())
                .andExpect(jsonPath("$.posts[0].title").value("译：Slow model")));
    }

    @Test
    void requiresSignInAndASupportedLanguage() throws Exception {
        mockMvc.perform(post("/api/translations").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("language", "zh-CN", "postIds", List.of(), "commentIds", List.of()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/translations").header("Authorization", bearer(newUser()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("language", "fr", "postIds", List.of(), "commentIds", List.of()))))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions translate(
            com.campusguard.user.User reader, List<UUID> postIds, List<UUID> commentIds) throws Exception {
        return mockMvc.perform(post("/api/translations").header("Authorization", bearer(reader))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("language", "zh-CN", "postIds", postIds, "commentIds", commentIds))));
    }
}
