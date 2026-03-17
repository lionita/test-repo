# Code Review Findings

## 1) `create(...)` accepts invalid pricing inputs that can crash bid placement (HIGH)
- `AuctionCommandService.create(...)` validates title/description/start/end times, but it does **not** validate `reservePrice` or `minIncrement` for null/positive values.
- `BiddingCommandService.placeBid(...)` later computes the minimum acceptable bid using `auction.reservePrice()` and `auction.minIncrement()`, which can trigger runtime failures when either value is null.
- Impact: auctions can be created in an invalid state and then fail unpredictably when the first bid is placed.

**Recommendation**
- In `create(...)`, enforce `reservePrice != null`, `reservePrice >= 0`, `minIncrement != null`, and `minIncrement > 0`.
- Add unit tests covering invalid pricing values.

## 2) `auction.settled` event payload encodes null winning bid as a string (MEDIUM)
- `AuctionCommandService.settle(...)` publishes JSON with `"winningBidId":"` + `settledAuction.winningBidId()` + `"`.
- If `winningBidId` is null (or if upstream guards regress), payload becomes `"winningBidId":"null"` instead of JSON null.
- Impact: downstream consumers can mis-handle settlement events because string `"null"` is not equivalent to null.

**Recommendation**
- Build the payload so `winningBidId` is emitted as either JSON null or a quoted UUID.
- Add a unit test that asserts the exact outbox payload for settlement.

## 3) Full reactor build/test is blocked in this environment due to Maven Central 403 (INFO)
- Running `mvn test -q` fails before tests execute because parent POM resolution for Spring Boot is denied from Maven Central in this environment.
- This appears to be an environment/network access issue rather than an application logic defect.

**Recommendation**
- Re-run in CI or a developer environment with Maven Central access.
- Optionally cache/host required dependencies internally to make builds deterministic in restricted networks.
