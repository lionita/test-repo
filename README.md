# test-repo

Multi-module Spring Boot reference implementation using Java 22, Spring Boot 3.5.9, PostgreSQL, REST, Spring Data JPA, Hexagonal-ish layering, outbox pattern, and JWT-based Spring Security checks.

## Modules
- `auction-module`: auction aggregate and auction lifecycle use cases.
- `bidding-module`: bidding use cases and bid-specific ports.
- `app-module`: Spring Boot app wiring REST adapters + JPA adapters + outbox publisher.

## JWT security checks
- API endpoints require a bearer JWT.
- `POST /api/auctions` and `POST /api/auctions/{id}/start` require `scope` claim containing `auction.write`.
- `POST /api/auctions/{id}/bids` requires `scope` claim containing `bid.write`.
- JWT must include a non-empty `sub` claim.

## Sample SQL
- `sql/sample-data.sql` contains sample inserts for auctions, bids, and outbox events.

## Run tests
```bash
mvn -pl bidding-module test
```

## Run app
```bash
mvn -pl app-module spring-boot:run
```
