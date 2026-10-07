package com.campusguard.post;

import com.campusguard.AbstractIntegrationTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthoredPostsIntegrationTest extends AbstractIntegrationTest {
    @Autowired PostRepository posts;
    @Autowired JdbcTemplate jdbc;

    @Test void pagesAcrossForumsWithoutDuplicatesLeaksOrDeletedPostsEvenWithEqualTimestamps() throws Exception {
        var author = newUser();
        var other = newUser();
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            var post = posts.saveAndFlush(new Post(uniqueForumKey(), author, "Own " + i, "Body"));
            ids.add(post.getId());
            jdbc.update("update posts set created_at = ? where id = ?", java.sql.Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")), post.getId());
        }
        var removed = posts.saveAndFlush(new Post(uniqueForumKey(), author, "Removed", "Body"));
        removed.softDelete(Instant.now()); posts.saveAndFlush(removed);
        posts.saveAndFlush(new Post(uniqueForumKey(), other, "Someone else's", "Body"));
        var returned = new ArrayList<UUID>();
        String cursor = null;
        for (int page = 0; page < 3; page++) {
            var request = get("/api/posts/authors/" + author.getId()).param("size", "2");
            if (cursor != null) request.param("cursor", cursor);
            String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var data = objectMapper.readTree(body);
            data.get("items").forEach(row -> {
                assertThat(row.get("author").get("id").asText()).isEqualTo(author.getId().toString());
                assertThat(row.get("author").has("email")).isFalse();
                assertThat(row.get("author").has("passwordHash")).isFalse();
                returned.add(UUID.fromString(row.get("id").asText()));
            });
            cursor = data.get("nextCursor").isNull() ? null : data.get("nextCursor").asText();
        }
        assertThat(returned).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(ids);
        assertThat(cursor).isNull();
        mockMvc.perform(get("/api/posts/authors/" + author.getId()).param("size", "101")).andExpect(status().isBadRequest());
    }
}
