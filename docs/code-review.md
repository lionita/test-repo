# Code Review Findings

## 1) Build is blocked by an unresolved Spring Boot parent version (HIGH)
- `app-module/pom.xml` pins the parent to `org.springframework.boot:spring-boot-starter-parent:4.0.0`.
- In this environment, Maven cannot resolve that parent version, which prevents the whole reactor from being built or tested.
- Reproduce: `mvn test -q` fails during model resolution before any module tests run.

**Recommendation**
- Pin to a resolvable Spring Boot release and keep the README version in sync.
- Consider adding CI validation that fails fast on dependency resolution drift.

## 2) Bids can be accepted after auction end time until scheduler closes the auction (HIGH)
- `BiddingCommandService.placeBid(...)` only checks `auction.status() == LIVE`; it does not validate `now <= auction.endTime()`.
- If scheduler execution is delayed, a `LIVE` auction may still accept late bids after the configured end time.

**Recommendation**
- Add a time check inside `placeBid(...)` using an injected clock/time source.
- Optionally auto-close (or reject with a clear error) when `now` is past `endTime`.

## 3) Auction start/close commands do not enforce schedule boundaries (MEDIUM)
- `AuctionCommandService.start(...)` allows start regardless of `startTime`.
- `AuctionCommandService.close(...)` allows close regardless of `endTime`.
- This enables operational mistakes (starting too early / closing too early) unless all callers enforce policy externally.

**Recommendation**
- Enforce temporal invariants in application/domain logic (`start` not before `startTime`, `close` not before `endTime`) or document intentional manual override semantics explicitly.
