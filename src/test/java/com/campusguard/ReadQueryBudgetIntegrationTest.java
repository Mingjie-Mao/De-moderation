package com.campusguard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusguard.comment.Comment;
import com.campusguard.comment.CommentRepository;
import com.campusguard.post.Post;
import com.campusguard.post.PostRepository;
import com.campusguard.user.User;
import com.campusguard.user.UserRole;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Guards against per-row lazy queries; diagnostics exclude fixture setup and login hashing. */
class ReadQueryBudgetIntegrationTest extends AbstractIntegrationTest {
    @Autowired EntityManagerFactory entities;
    @Autowired PostRepository posts;
    @Autowired CommentRepository comments;

    @Test void twentyAuthorsDoNotMultiplyQueriesOnForumReads() throws Exception {
        var authors = new ArrayList<User>();
        String fixtureHash = passwordEncoder.encode(RAW_PASSWORD);
        for (int i = 0; i < 20; i++)
            authors.add(new User("budget_" + UUID.randomUUID().toString().substring(0, 8), fixtureHash, UserRole.MEMBER));
        authors = new ArrayList<>(userRepository.saveAllAndFlush(authors));
        String forum = uniqueForumKey();
        var rows = new ArrayList<Post>();
        for (User author : authors) rows.add(new Post(forum, author, "Query budget", "Body", null));
        rows = new ArrayList<>(posts.saveAllAndFlush(rows));
        Post parent = rows.getFirst();
        var replies = new ArrayList<Comment>();
        for (User author : authors) replies.add(new Comment(parent, null, author, "Reply", null));
        comments.saveAllAndFlush(replies);
        String token = bearer(authors.getFirst());
        var statistics = entities.unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            budget("health_anonymous", get("/actuator/health/readiness"), 0);
            budget("health_authenticated", get("/actuator/health/readiness").header("Authorization", token), 1);
            budget("feed_anonymous", get("/api/posts").param("forum", forum).param("size", "20"), 1);
            budget("feed_authenticated", get("/api/posts").param("forum", forum).param("size", "20").header("Authorization", token), 2);
            budget("comments_anonymous", get("/api/posts/" + parent.getId() + "/comments").param("size", "20"), 2);
            budget("comments_authenticated", get("/api/posts/" + parent.getId() + "/comments").param("size", "20").header("Authorization", token), 3);
            budget("profile_authenticated", get("/api/users/me").header("Authorization", token), 1);
            budget("author_posts_anonymous", get("/api/posts/authors/" + authors.getFirst().getId()), 1);
        } finally {
            statistics.setStatisticsEnabled(previous);
        }
    }

    private void budget(String name, MockHttpServletRequestBuilder request, int limit) throws Exception {
        var statistics = entities.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        mockMvc.perform(request).andExpect(status().isOk());
        long sql = statistics.getPrepareStatementCount();
        System.out.printf("READ_QUERY_BUDGET endpoint=%s statements=%d transactions=%d%n",
                name, sql, statistics.getTransactionCount());
        assertThat(sql).as("Bounded SQL count for %s with 20 different authors", name).isLessThanOrEqualTo(limit);
    }
}
