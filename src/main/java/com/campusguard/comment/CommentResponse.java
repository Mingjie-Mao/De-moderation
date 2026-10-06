package com.campusguard.comment;

import com.campusguard.common.AuthorView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A comment with its parent id. List responses are flat and paged; the replies
 * field remains empty for older clients that already know this response shape.
 */
public record CommentResponse(
        UUID id,
        UUID parentCommentId,
        AuthorView author,
        String body,
        String mediaUrl,
        Instant createdAt,
        List<CommentResponse> replies) {

    public CommentResponse(
            UUID id, UUID parentCommentId, AuthorView author, String body,
            Instant createdAt, List<CommentResponse> replies) {
        this(id, parentCommentId, author, body, null, createdAt, replies);
    }
}
