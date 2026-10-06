-- Only undo content removal that this case actually performed. A reported
-- author may delete their own post before review; a later appeal must not
-- publish it again.
ALTER TABLE moderation_cases
    ADD COLUMN content_hidden_by_case BOOLEAN NOT NULL DEFAULT false;

-- Existing cases have no flag. Recover it only where the audit trail proves a
-- hide was performed and no later restoration for the same case superseded it.
UPDATE moderation_cases AS c
SET content_hidden_by_case = true
WHERE c.status = 'RESOLVED'
  AND c.final_action IN ('HIDE', 'DELETE', 'BAN')
  AND EXISTS (
      SELECT 1
      FROM audit_log AS hidden
      WHERE hidden.action = 'CONTENT_HIDDEN'
        AND hidden.target_type = c.target_type
        AND hidden.target_id = c.target_id
        AND hidden.payload ->> 'caseId' = c.id::text
        AND NOT EXISTS (
            SELECT 1
            FROM audit_log AS restored
            WHERE restored.action = 'CONTENT_RESTORED'
              AND restored.target_type = c.target_type
              AND restored.target_id = c.target_id
              AND restored.payload ->> 'caseId' = c.id::text
              AND restored.created_at >= hidden.created_at
        )
  );
