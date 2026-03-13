# Auction System Gap Analysis

This document compares the implemented codebase against the target capabilities in `docs/auction-system-design.md`.

## Implemented Today

- Auction lifecycle endpoints are implemented:
  - create (`POST /api/auctions`)
  - start (`POST /api/auctions/{auctionId}/start`)
  - close (`POST /api/auctions/{auctionId}/close`)
  - settle (`POST /api/auctions/{auctionId}/settle`)
- Scheduled auto-close is implemented via `AuctionClosingScheduler` using `closeExpiredAuctions(...)`.
- Bid placement is implemented with transactional locking semantics and validation:
  - auction must be `LIVE`
  - auction must not be past `endTime`
  - amount must satisfy reserve/current+min increment rule
  - idempotency key required
  - bidder authorization/blocked/deleted checks via `BidderPurchasingAuthorizationPort`
- Bid sequencing and constraints are implemented:
  - unique `(auction_id, sequence_number)`
  - unique `(auction_id, idempotency_key)`
- Bidder onboarding/admin endpoints are implemented:
  - onboard, update, soft-delete, block, unblock
- Realtime push exists using SSE endpoint (`/api/realtime/events`) and in-process fan-out.
- Transactional outbox flow exists:
  - outbox append on domain actions
  - scheduled claim-and-publish loop
  - retries + dead-letter timestamp fields
- JWT resource-server security is implemented with scope checks and required JWT `sub`.

## Gaps vs Target Design

### 1) Architecture shape differs from design
- Design calls for separate API Gateway, Auction, Bid, Realtime, Settlement, and Notification services.
- Current implementation is a modular monolith (single Spring Boot deployable) with in-process boundaries.

### 2) Auction status parity gap
- Design includes `cancelled` status.
- Domain enum currently supports only `SCHEDULED`, `LIVE`, `CLOSED`, `SETTLED`.

### 3) Messaging backbone mismatch
- Design assumes external event bus (e.g., Kafka) for domain event propagation.
- Current publisher uses Spring `ApplicationEventPublisher` (in-process), so durability and cross-service delivery semantics differ.

### 4) Realtime scalability gap
- Design expects horizontally scalable realtime with pub/sub backplane.
- Current SSE emitter registry is in-memory per app instance.

### 5) Missing platform components from design
- No Redis cache/leaderboard path.
- No object storage integration for media assets/immutable audit export.
- No Settlement Service or Notification Service integration consuming domain events.

### 6) Consistency/retry strategy gap
- Design calls out optimistic retries for transient serialization conflicts.
- Current critical path relies primarily on pessimistic row locking and does not implement an explicit optimistic retry policy.

### 7) Security/compliance/observability gaps
- JWT auth is present for client-facing APIs, but design-level service-to-service mTLS is not applicable/implemented in current single-service topology.
- No signed immutable audit-log pipeline.
- No implementation for the design’s target metrics/tracing/correlation-ID standards.

## Recommended Next Steps
1. Decide whether to keep the modular monolith for MVP or split services per design; align document language either way.
2. Add/omit `CANCELLED` status intentionally and wire lifecycle/API behavior accordingly.
3. Replace in-process event publisher with external broker integration if cross-service consumers are required.
4. Add observability baseline (metrics + tracing + correlation IDs) before scaling.
