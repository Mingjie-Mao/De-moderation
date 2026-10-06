-- A report must keep the version it concerned. Authors may edit or remove
-- content after filing; a later review and a decision corpus must not silently
-- substitute the newest text for the reported text.
ALTER TABLE moderation_cases
    ADD COLUMN reported_at TIMESTAMPTZ,
    ADD COLUMN reported_title VARCHAR(200),
    ADD COLUMN reported_body TEXT,
    ADD COLUMN reported_author_id UUID,
    ADD COLUMN reported_media_id UUID REFERENCES media_objects (id);

ALTER TABLE moderation_cases
    ADD CONSTRAINT moderation_cases_evidence_complete
    CHECK (
        (reported_at IS NULL AND reported_title IS NULL AND reported_body IS NULL
            AND reported_author_id IS NULL AND reported_media_id IS NULL)
        OR
        (reported_at IS NOT NULL AND reported_body IS NOT NULL
            AND reported_author_id IS NOT NULL)
    );

CREATE INDEX idx_moderation_cases_reported_media
    ON moderation_cases (reported_media_id)
    WHERE reported_media_id IS NOT NULL;
