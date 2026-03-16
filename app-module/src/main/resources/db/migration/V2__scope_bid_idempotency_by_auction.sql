ALTER TABLE auction.bids
    DROP CONSTRAINT IF EXISTS uq_bidder_idempotency;

ALTER TABLE auction.bids
    ADD CONSTRAINT uq_bidder_idempotency UNIQUE (auction_id, bidder_id, idempotency_key);
