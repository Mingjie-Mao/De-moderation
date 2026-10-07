-- Machine translations of posts and comments, shared by every viewer who reads
-- the same content in the same language. source_hash is the text that was
-- translated: an edit changes it, and the stale translation is not served.
CREATE TABLE content_translations (
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('POST', 'COMMENT')),
    target_id UUID NOT NULL,
    language VARCHAR(10) NOT NULL CHECK (language IN ('en', 'zh-CN')),
    source_hash CHAR(64) NOT NULL,
    title TEXT,
    body TEXT NOT NULL,
    model VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (target_type, target_id, language)
);
