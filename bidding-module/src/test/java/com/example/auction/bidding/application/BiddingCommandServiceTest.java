package com.example.auction.bidding.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.domain.BidStatus;
import com.example.auction.bidding.ports.BidRepositoryPort;
import com.example.auction.bidding.ports.BidderPurchasingAuthorizationPort;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BiddingCommandServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void placesValidBidAndWritesOutbox() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult result = service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");

        assertEquals(BidStatus.ACCEPTED, result.status());
        assertNull(result.rejectReason());
        assertEquals(new BigDecimal("100.00"), auctions.findById(id).orElseThrow().currentPrice());
        assertEquals(1, bids.seq.get(id));
        assertTrue(outbox.events.contains("bid.placed"));
    }

    @Test
    void reusesStoredDecisionWhenIdempotencyKeyAlreadyExists() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));
        bids.decisions.put(id + ":u1:k1", new BidRepositoryPort.BidDecision(BidStatus.REJECTED, "auction is not live"));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult result = service.placeBid(id, "u1", new BigDecimal("90.00"), "k1");

        assertEquals(BidStatus.REJECTED, result.status());
        assertEquals("auction is not live", result.rejectReason());
        assertTrue(bids.savedBids.isEmpty());
        assertEquals(0, bidderAuthorization.calls);
        assertTrue(outbox.events.isEmpty());
        assertNull(auctions.findById(id).orElseThrow().currentPrice());
    }

    @Test
    void rejectsBelowMinimumBid() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult result =
                service.placeBid(id, "u1", new BigDecimal("99.99"), "k1");
        assertEquals(BidStatus.REJECTED, result.status());
        assertTrue(result.rejectReason().startsWith("bid must be >="));
        assertEquals(0, bidderAuthorization.calls);
        assertTrue(outbox.events.contains("bid.rejected"));
    }

    @Test
    void rejectsBlankBidderId() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        assertThrows(IllegalArgumentException.class,
                () -> service.placeBid(id, "   ", new BigDecimal("100.00"), "k1"));
    }

    @Test
    void rejectsNonPositiveAmount() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        assertThrows(IllegalArgumentException.class,
                () -> service.placeBid(id, "u1", BigDecimal.ZERO, "k1"));
    }

    @Test
    void rejectsBlankIdempotencyKey() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        assertThrows(IllegalArgumentException.class,
                () -> service.placeBid(id, "u1", new BigDecimal("100.00"), ""));
    }

    @Test
    void rejectsBidWhenLockedAuctionStateIsAlreadyClosed() {
        var auctions = new LockAwareInMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.findByIdSnapshot = new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"),
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null);
        auctions.findByIdForUpdateSnapshot = new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"),
                OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.CLOSED, null, null);

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);

        BiddingCommandService.BidPlacementResult result =
                service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");
        assertEquals(BidStatus.REJECTED, result.status());
        assertEquals("auction is not live", result.rejectReason());
        assertTrue(bids.seq.isEmpty());
        assertTrue(outbox.events.contains("bid.rejected"));
    }

    @Test
    void rejectsBidWhenBidderHasInsufficientAuthorization() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        bidderAuthorization.authorized = false;
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult result =
                service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");
        assertEquals(BidStatus.REJECTED, result.status());
        assertEquals("bidder has insufficient purchasing authorization", result.rejectReason());
        assertTrue(outbox.events.contains("bid.rejected"));
    }


    @Test
    void rejectsBidAfterAuctionEndTime() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-01-01T12:00:00Z");
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), start, end, AuctionStatus.LIVE, null, null));

        Clock afterEnd = Clock.fixed(Instant.parse("2026-01-01T12:00:01Z"), ZoneOffset.UTC);
        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization, afterEnd);

        BiddingCommandService.BidPlacementResult result =
                service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");
        assertEquals(BidStatus.REJECTED, result.status());
        assertEquals("auction has already ended", result.rejectReason());
        assertTrue(outbox.events.contains("bid.rejected"));
    }

    @Test
    void replaysAcceptedDecisionWhenSaveHitsIdempotencyConstraint() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));
        bids.throwIdempotencyConflictOnNextSave = true;
        bids.decisions.put(id + ":u1:k1", new BidRepositoryPort.BidDecision(BidStatus.ACCEPTED, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult result =
                service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");

        assertEquals(BidStatus.ACCEPTED, result.status());
        assertNull(result.rejectReason());
        assertTrue(outbox.events.isEmpty());
        assertNull(auctions.findById(id).orElseThrow().currentPrice());
    }

    @Test
    void replaysRejectedDecisionWhenRejectSaveHitsIdempotencyConstraint() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.CLOSED, null, null));
        bids.throwIdempotencyConflictOnNextSave = true;
        bids.decisions.put(id + ":u1:k1", new BidRepositoryPort.BidDecision(BidStatus.REJECTED, "auction is not live"));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult result =
                service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");

        assertEquals(BidStatus.REJECTED, result.status());
        assertEquals("auction is not live", result.rejectReason());
        assertTrue(outbox.events.isEmpty());
    }

    @Test
    void idempotencyKeyCanBeReusedAcrossDifferentAuctions() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();

        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        auctions.save(new Auction(a1, "A1", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));
        auctions.save(new Auction(a2, "A2", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        BiddingCommandService.BidPlacementResult first = service.placeBid(a1, "u1", new BigDecimal("100.00"), "same-key");
        BiddingCommandService.BidPlacementResult second = service.placeBid(a2, "u1", new BigDecimal("100.00"), "same-key");

        assertEquals(BidStatus.ACCEPTED, first.status());
        assertEquals(BidStatus.ACCEPTED, second.status());
        assertEquals(2, bids.savedBids.size());
        assertTrue(bids.savedBids.contains(a1 + ":u1:same-key"));
        assertTrue(bids.savedBids.contains(a2 + ":u1:same-key"));
    }

    @Test
    void rejectedOutboxPayloadIsValidJsonWhenValuesContainQuotes() throws Exception {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        bidderAuthorization.authorized = false;
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        service.placeBid(id, "u\"1", new BigDecimal("100.00"), "k1");

        assertFalse(outbox.payloads.isEmpty());
        String payload = outbox.payloads.get(0);
        var node = OBJECT_MAPPER.readTree(payload);
        assertEquals(id.toString(), node.get("auctionId").asText());
        assertEquals("u\"1", node.get("bidderId").asText());
    }

    static class InMemAuctions implements AuctionRepositoryPort {
        Map<UUID, Auction> data = new HashMap<>();
        public Auction save(Auction auction) { data.put(auction.id(), auction); return auction; }
        public Optional<Auction> findById(UUID id) { return Optional.ofNullable(data.get(id)); }
        public List<Auction> findLiveEndingAtOrBefore(OffsetDateTime threshold) { return List.of(); }
    }
    static class LockAwareInMemAuctions implements AuctionRepositoryPort {
        Auction findByIdSnapshot;
        Auction findByIdForUpdateSnapshot;

        @Override
        public Auction save(Auction auction) {
            findByIdSnapshot = auction;
            findByIdForUpdateSnapshot = auction;
            return auction;
        }

        @Override
        public Optional<Auction> findById(UUID id) {
            return Optional.ofNullable(findByIdSnapshot);
        }

        @Override
        public Optional<Auction> findByIdForUpdate(UUID id) {
            return Optional.ofNullable(findByIdForUpdateSnapshot);
        }

        @Override
        public List<Auction> findLiveEndingAtOrBefore(OffsetDateTime threshold) {
            return List.of();
        }
    }

    static class InMemBids implements BidRepositoryPort {
        Map<UUID, Long> seq = new HashMap<>();
        Map<String, BidDecision> decisions = new HashMap<>();
        List<String> savedBids = new ArrayList<>();
        boolean throwIdempotencyConflictOnNextSave = false;

        public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, Long sequenceNumber, BidStatus bidStatus, String rejectReason) {
            if (throwIdempotencyConflictOnNextSave) {
                throwIdempotencyConflictOnNextSave = false;
                throw new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_bidder_idempotency\"");
            }
            if (sequenceNumber != null) {
                seq.put(auctionId, sequenceNumber);
            }
            decisions.put(auctionId + ":" + bidderId + ":" + idempotencyKey, new BidDecision(bidStatus, rejectReason));
            savedBids.add(auctionId + ":" + bidderId + ":" + idempotencyKey);
        }

        public Optional<BidDecision> findByAuctionIdAndBidderIdAndIdempotencyKey(UUID auctionId, String bidderId, String idempotencyKey) {
            return Optional.ofNullable(decisions.get(auctionId + ":" + bidderId + ":" + idempotencyKey));
        }

        public long nextSequence(UUID auctionId) { return seq.getOrDefault(auctionId, 0L) + 1; }
        public Optional<WinningBid> findWinningBid(UUID auctionId) { return Optional.empty(); }
    }
    static class InMemOutbox implements OutboxPort {
        List<String> events = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        public void append(String eventType, UUID aggregateId, String payload) {
            events.add(eventType);
            payloads.add(payload);
        }
    }

    static class InMemBidderAuthorization implements BidderPurchasingAuthorizationPort {
        boolean authorized = true;
        int calls = 0;

        @Override
        public boolean hasSufficientAuthorization(String bidderId, BigDecimal amount) {
            calls++;
            return authorized;
        }
    }
}
