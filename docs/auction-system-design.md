# Auction System Design

## Goals
- Support real-time bidding with low latency updates.
- Ensure bids are valid, ordered, and tamper-resistant.
- Provide clear auction lifecycle management (scheduled, live, closed, settled).

## Core Requirements
- **Create auctions** with reserve price, start/end time, and item metadata.
- **Place bids** with strict validation:
  - Auction must be live.
  - Bid must exceed current highest bid by minimum increment.
  - Bidder must have sufficient purchasing authorization.
- **Real-time updates** for active participants.
- **Close and settle** auctions deterministically.
- **Auditability** for every state transition.

## High-Level Architecture

### Services
1. **API Gateway**
   - AuthN/AuthZ, rate limiting, request routing.
2. **Auction Service**
   - Auction CRUD, lifecycle orchestration, winner selection.
3. **Bid Service**
   - Bid ingestion, validation, ordering, idempotency.
4. **Realtime Service**
   - WebSocket/SSE fan-out for live bid updates and countdowns.
5. **Settlement Service**
   - Payment authorization/capture and final settlement workflow.
6. **Notification Service**
   - Email/push/webhook notifications for outbid/won/lost events.

### Data Stores
- **Relational DB (PostgreSQL)** for authoritative auction and bid records.
- **Redis** for hot read cache and ephemeral leaderboards.
- **Object storage** for media assets and immutable audit exports.

### Messaging
- **Event bus** (e.g., Kafka) for domain events:
  - `auction.created`
  - `auction.started`
  - `bid.placed`
  - `auction.closed`
  - `auction.settled`

## Data Model (simplified)

### `auctions`
- `id` (UUID)
- `title`
- `description`
- `reserve_price`
- `min_increment`
- `start_time`
- `end_time`
- `status` (`scheduled|live|closed|settled|cancelled`)
- `winning_bid_id` (nullable)

### `bids`
- `id` (UUID)
- `auction_id` (FK)
- `bidder_id`
- `amount`
- `placed_at`
- `sequence_number` (monotonic per auction)
- `idempotency_key`

## Critical Workflows

### Place Bid
1. Client sends authenticated `POST /auctions/{id}/bids` with idempotency key.
2. Bid Service validates auction status and minimum increment.
3. Transaction locks auction row (`SELECT ... FOR UPDATE`) to prevent race conditions.
4. New bid is persisted with incremented `sequence_number`.
5. Highest-bid cache and read models update asynchronously.
6. Realtime event broadcast to subscribers.

### Close Auction
1. Scheduler or manual action marks auction closed at `end_time`.
2. Highest valid bid selected as winner (if reserve met).
3. `auction.closed` event emitted.
4. Settlement Service attempts capture and transitions to `settled` (or exception queue).

## Consistency and Concurrency
- Use DB transactions for bid acceptance path.
- Enforce unique `(auction_id, sequence_number)`.
- Enforce unique `(bidder_id, idempotency_key)` to prevent duplicate submissions.
- Use optimistic retries for transient serialization conflicts.

## Scalability
- Partition active auctions by `auction_id` for bid processing workers.
- Horizontal scale Realtime Service with pub/sub backplane.
- Read-heavy endpoints served via cache and denormalized projections.

## Security and Compliance
- OAuth/JWT for user auth; service-to-service mTLS.
- Server-side validation for all bid rules (never trust client).
- Signed audit logs and immutable event retention.
- PII minimization and encryption at rest/in transit.

## Observability
- Metrics: bid latency, rejection rate, websocket fan-out lag, settlement success rate.
- Tracing across API Gateway → Bid Service → DB → Event Bus.
- Structured logs with correlation IDs.

## Open Questions
- Should we support anti-sniping (automatic end-time extension)?
- Are proxy bids / max auto-bids required in MVP?
- What settlement providers and fallback flows are needed?
