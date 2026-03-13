-- PostgreSQL schema derived from JPA entities in app-module.
-- Compatible with PostgreSQL 13+.

CREATE TABLE IF NOT EXISTS auctions (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(4000) NOT NULL,
    reserve_price NUMERIC(19,2) NOT NULL,
    min_increment NUMERIC(19,2) NOT NULL,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_price NUMERIC(19,2),
    winning_bid_id UUID,
    CONSTRAINT chk_auctions_status
        CHECK (status IN ('SCHEDULED', 'LIVE', 'CLOSED', 'SETTLED')),
    CONSTRAINT chk_auctions_prices
        CHECK (reserve_price >= 0 AND min_increment > 0),
    CONSTRAINT chk_auctions_time_window
        CHECK (end_time > start_time)
);

CREATE TABLE IF NOT EXISTS bidders (
    id VARCHAR(255) PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    national_id VARCHAR(50) NOT NULL,
    purchasing_authorization_limit NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    blocked_until TIMESTAMPTZ,
    CONSTRAINT chk_bidders_purchasing_limit
        CHECK (purchasing_authorization_limit >= 0)
);

CREATE TABLE IF NOT EXISTS bids (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL,
    bidder_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    sequence_number BIGINT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bid_auction_sequence UNIQUE (auction_id, sequence_number),
    CONSTRAINT uq_bidder_idempotency UNIQUE (bidder_id, idempotency_key),
    CONSTRAINT fk_bids_auction FOREIGN KEY (auction_id) REFERENCES auctions(id),
    CONSTRAINT fk_bids_bidder FOREIGN KEY (bidder_id) REFERENCES bidders(id),
    CONSTRAINT chk_bids_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_bids_sequence_positive CHECK (sequence_number > 0)
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_auctions_winning_bid'
    ) THEN
        ALTER TABLE auctions
            ADD CONSTRAINT fk_auctions_winning_bid
            FOREIGN KEY (winning_bid_id) REFERENCES bids(id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    aggregate_id UUID NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INT NOT NULL,
    next_attempt_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    last_error TEXT,
    CONSTRAINT chk_outbox_publish_attempts_non_negative CHECK (publish_attempts >= 0)
);

-- Query-supporting indexes inferred from Spring Data repository methods.
CREATE INDEX IF NOT EXISTS idx_auctions_status_end_time
    ON auctions (status, end_time);

CREATE INDEX IF NOT EXISTS idx_bids_auction_amount_sequence
    ON bids (auction_id, amount DESC, sequence_number ASC);

CREATE INDEX IF NOT EXISTS idx_outbox_events_ready
    ON outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
