# test-repo

Spring Boot reference implementation of an auction service using Java 22, Spring Boot 3.5.9, PostgreSQL, REST, Spring Data JPA, Hexagonal-ish layering, and an outbox pattern.

## Documents
- [Auction System Design](docs/auction-system-design.md)

## Architecture at a glance
- **Inbound adapter**: REST API (`AuctionController`).
- **Application layer**: `AuctionService` use cases.
- **Ports**: `AuctionRepositoryPort`, `BidRepositoryPort`, `OutboxPort`.
- **Outbound adapters**: Spring Data JPA persistence adapters for auctions, bids, and outbox events.
- **Outbox publisher**: scheduled publisher that marks events as published.

## Tech stack
- Java 22
- Spring Boot 3.5.9
- Spring Web
- Spring Data JPA
- PostgreSQL

## Run tests
```bash
mvn test
```

## Run locally
1. Start PostgreSQL and create database/user matching `src/main/resources/application.yml`.
2. Run:
   ```bash
   mvn spring-boot:run
   ```