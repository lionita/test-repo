-- Sample schema-compatible seed data for PostgreSQL
-- Assumes tables are created by Hibernate (ddl-auto=update) or equivalent migrations.

INSERT INTO auctions (id, title, description, reserve_price, min_increment, start_time, end_time, status, current_price, winning_bid_id)
VALUES (
  '11111111-1111-1111-1111-111111111111',
  'Vintage Watch',
  'Restored 1960s mechanical watch in excellent condition',
  100.00,
  10.00,
  NOW() - INTERVAL '2 hours',
  NOW() + INTERVAL '2 hours',
  'LIVE',
  120.00,
  NULL
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO bids (id, auction_id, bidder_id, amount, sequence_number, idempotency_key, created_at)
VALUES
 ('22222222-2222-2222-2222-222222222221', '11111111-1111-1111-1111-111111111111', 'alice', 100.00, 1, 'alice-1', NOW()),
 ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'bob', 120.00, 2, 'bob-1', NOW())
ON CONFLICT (id) DO NOTHING;

INSERT INTO outbox_events (id, event_type, aggregate_id, payload, created_at, published_at)
VALUES
 ('33333333-3333-3333-3333-333333333331', 'auction.created', '11111111-1111-1111-1111-111111111111', '{"auctionId":"11111111-1111-1111-1111-111111111111"}', NOW(), NOW()),
 ('33333333-3333-3333-3333-333333333332', 'bid.placed', '11111111-1111-1111-1111-111111111111', '{"auctionId":"11111111-1111-1111-1111-111111111111","amount":120.00,"sequence":2}', NOW(), NULL)
ON CONFLICT (id) DO NOTHING;
