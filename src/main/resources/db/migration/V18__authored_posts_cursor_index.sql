-- Supports public author pages without scanning every forum or counting rows.
create index idx_posts_author_created_live
    on posts (author_id, created_at desc, id desc) where deleted_at is null;
