package com.example.auction.bidding.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.ports.BidRepositoryPort;
import com.example.auction.bidding.ports.BidderPurchasingAuthorizationPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BiddingCommandServiceTest {
    @Test
    void placesValidBidAndWritesOutbox() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");

        assertEquals(new BigDecimal("100.00"), auctions.findById(id).orElseThrow().currentPrice());
        assertEquals(1, bids.seq.get(id));
        assertTrue(outbox.events.contains("bid.placed"));
    }

    @Test
    void returnsWithoutSideEffectsWhenIdempotencyKeyAlreadyExists() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        var bidderAuthorization = new InMemBidderAuthorization();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, "Test auction", "desc", new BigDecimal("100.00"), new BigDecimal("10.00"), OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), AuctionStatus.LIVE, null, null));
        bids.existingKeys.add(id + ":k1");

        var service = new BiddingCommandService(auctions, bids, outbox, bidderAuthorization);
        service.placeBid(id, "u1", new BigDecimal("90.00"), "k1");

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
        assertThrows(IllegalArgumentException.class,
                () -> service.placeBid(id, "u1", new BigDecimal("99.99"), "k1"));
        assertEquals(0, bidderAuthorization.calls);
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

        assertThrows(IllegalStateException.class,
                () -> service.placeBid(id, "u1", new BigDecimal("100.00"), "k1"));
        assertTrue(bids.seq.isEmpty());
        assertTrue(outbox.events.isEmpty());
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
        assertThrows(IllegalStateException.class,
                () -> service.placeBid(id, "u1", new BigDecimal("100.00"), "k1"));
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
        Set<String> existingKeys = new HashSet<>();
        List<String> savedBids = new ArrayList<>();

        public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber) {
            seq.put(auctionId, sequenceNumber);
            existingKeys.add(auctionId + ":" + idempotencyKey);
            savedBids.add(auctionId + ":" + idempotencyKey);
        }

        public boolean existsByAuctionIdAndIdempotencyKey(UUID auctionId, String idempotencyKey) {
            return existingKeys.contains(auctionId + ":" + idempotencyKey);
        }

        public long nextSequence(UUID auctionId) { return seq.getOrDefault(auctionId, 0L) + 1; }
        public Optional<WinningBid> findWinningBid(UUID auctionId) { return Optional.empty(); }
    }
    static class InMemOutbox implements OutboxPort {
        List<String> events = new ArrayList<>();
        public void append(String eventType, UUID aggregateId, String payload) { events.add(eventType); }
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
