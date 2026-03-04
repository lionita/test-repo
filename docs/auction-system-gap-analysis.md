# Auction System Gap Analysis

This document compares the implemented codebase against the target capabilities in `docs/auction-system-design.md`.

## Implemented Today

- Auction creation with `title`, `description`, `reservePrice`, `minIncrement`, `startTime`, and `endTime`, initial status `SCHEDULED`, and `auction.created` outbox event. 
- Manual auction start (`/api/auctions/{id}/start`) transitioning `SCHEDULED -> LIVE` and writing `auction.started` outbox event.
- Bid placement (`/api/auctions/{id}/bids`) with validation for:
  - live auction status,
  - positive bid amount,
  - non-blank bidder/idempotency fields,
  - minimum acceptable amount based on reserve or current + increment.
- Bid sequencing and DB constraints for `(auction_id, sequence_number)` and `(auction_id, idempotency_key)`.
- Transactional outbox table with scheduled publisher stub (logs/marks published).
- JWT scope and `sub`-presence checks on API endpoints.

## Missing / Not Yet Implemented

### 1) Auction lifecycle is incomplete
- No `end_time` on auctions and no scheduled auto-close at end time.
- No close operation/use case (`auction.closed` workflow) and no winner selection.
- No settlement step or `settled` transition/event handling.
- Current domain only models start transition; no explicit close/settle/cancel transitions.

### 2) Auction model still has partial parity gaps
- Item/media metadata beyond title/description is still not modeled.
- `winning_bid_id` is now present in schema/domain, but winner selection logic is not implemented yet.

### 3) Bid workflow gaps
- No bidder purchasing authorization check.
- Idempotency uniqueness is scoped by auction (`auctionId + idempotencyKey`), while design calls out bidder-scoped uniqueness (`bidder_id + idempotency_key`).
- No explicit optimistic retry handling for serialization conflicts.

### 4) Real-time and read model features are absent
- No WebSocket/SSE realtime fan-out service for live updates/countdowns.
- No cache/read-model updates (e.g., Redis leaderboard/projection path).

### 5) Messaging and integration gaps
- Outbox exists, but no actual event-bus publisher integration (e.g., Kafka producer).
- No downstream settlement/notification services wired to consume domain events.

### 6) Data/consistency differences vs design
- No persisted bid `placed_at`/domain-level ordering semantics beyond sequence + created timestamp in JPA entity.
- No explicit DB-level guarantee shown for `(bidder_id, idempotency_key)`.

### 7) Security/compliance/observability gaps
- JWT auth is present, but no service-to-service mTLS concerns in this app.
- No signed/immutable audit log export pipeline.
- No metrics/tracing/correlation-id implementation matching observability targets.

## Suggested Next Milestone (MVP completion order)
1. Extend auction schema/domain with `title`, `description`, `startTime`, `endTime`, `winningBidId`.
2. Add close-auction use case (scheduler + manual endpoint) with winner selection and `auction.closed` event.
3. Add settlement workflow and `auction.settled` event.
4. Add bidder purchasing authorization port + implementation.
5. Add real-time push adapter (SSE/WebSocket) triggered from `bid.placed`/`auction.closed`.
6. Replace outbox log stub with real event-bus publisher and retries/dead-letter handling.
