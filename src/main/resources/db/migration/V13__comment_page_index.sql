-- Both roots and replies now share one bounded keyset walk.
CREATE INDEX idx_comments_live_post_page
    ON comments (post_id, created_at, id)
    WHERE deleted_at IS NULL;
