-- Presentation metadata travels with real posts, rather than device-only maps.
ALTER TABLE posts ADD COLUMN category varchar(50) NOT NULL DEFAULT 'Study';
ALTER TABLE posts ADD COLUMN pin_rank integer CHECK(pin_rank IS NULL OR pin_rank>=0);
-- Imported virtual-market history is distinguishable from live observations.
ALTER TABLE market_candles ADD COLUMN source varchar(30) NOT NULL DEFAULT 'LIVE';
ALTER TABLE market_trades ADD COLUMN source varchar(30) NOT NULL DEFAULT 'LIVE';
-- Explicit operator imports are one-time, transactional and auditable.
CREATE TABLE demo_data_batches (batch_key varchar(100) PRIMARY KEY, created_at timestamptz NOT NULL DEFAULT now(), summary jsonb NOT NULL);
