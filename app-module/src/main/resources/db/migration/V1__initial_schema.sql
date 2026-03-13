CREATE SCHEMA IF NOT EXISTS auction;

CREATE TABLE auction.auctions (
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
        CHECK (status IN ('DRAFT', 'SCHEDULED', 'LIVE', 'CLOSED', 'SETTLED', 'CANCELLED')),
    CONSTRAINT chk_auctions_prices
        CHECK (reserve_price >= 0 AND min_increment > 0),
    CONSTRAINT chk_auctions_time_window
        CHECK (end_time > start_time)
);

CREATE TABLE auction.bidders (
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

CREATE TABLE auction.bids (
    id UUID PRIMARY KEY,
    auction_id UUID NOT NULL,
    bidder_id VARCHAR(255) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    sequence_number BIGINT,
    idempotency_key VARCHAR(255) NOT NULL,
    bid_status VARCHAR(16) NOT NULL,
    reject_reason VARCHAR(1024),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_bid_auction_sequence UNIQUE (auction_id, sequence_number),
    CONSTRAINT uq_bidder_idempotency UNIQUE (bidder_id, idempotency_key),
    CONSTRAINT fk_bids_auction FOREIGN KEY (auction_id) REFERENCES auction.auctions(id),
    CONSTRAINT fk_bids_bidder FOREIGN KEY (bidder_id) REFERENCES auction.bidders(id),
    CONSTRAINT chk_bids_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_bids_sequence_positive CHECK (sequence_number IS NULL OR sequence_number > 0),
    CONSTRAINT chk_bids_status CHECK (bid_status IN ('ACCEPTED', 'REJECTED')),
    CONSTRAINT chk_bids_status_reason CHECK (
        (bid_status = 'ACCEPTED' AND reject_reason IS NULL)
        OR (bid_status = 'REJECTED' AND reject_reason IS NOT NULL)
    ),
    CONSTRAINT chk_bids_status_sequence CHECK (
        (bid_status = 'ACCEPTED' AND sequence_number IS NOT NULL)
        OR (bid_status = 'REJECTED' AND sequence_number IS NULL)
    )
);

ALTER TABLE auction.auctions
    ADD CONSTRAINT fk_auctions_winning_bid
    FOREIGN KEY (winning_bid_id) REFERENCES auction.bids(id);

CREATE TABLE auction.outbox_events (
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

CREATE INDEX idx_auctions_status_end_time
    ON auction.auctions (status, end_time);

CREATE INDEX idx_bids_auction_amount_sequence
    ON auction.bids (auction_id, bid_status, amount DESC, sequence_number ASC);

CREATE INDEX idx_outbox_events_ready
    ON auction.outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
