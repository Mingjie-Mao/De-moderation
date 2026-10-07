-- Evidence translations are separate from live POST/COMMENT versions.
ALTER TABLE content_translations DROP CONSTRAINT content_translations_target_type_check;
ALTER TABLE content_translations ADD CONSTRAINT content_translations_target_type_check
    CHECK (target_type IN ('POST', 'COMMENT', 'EVIDENCE'));
