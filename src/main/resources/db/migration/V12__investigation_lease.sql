-- Reserve a case before a paid investigation. The reservation is committed
-- before any model call; a crashed worker can be replaced after expiry.
CREATE TABLE investigation_leases (
    case_id UUID PRIMARY KEY REFERENCES moderation_cases (id) ON DELETE CASCADE,
    owner_token UUID NOT NULL,
    lease_until TIMESTAMPTZ NOT NULL
);
