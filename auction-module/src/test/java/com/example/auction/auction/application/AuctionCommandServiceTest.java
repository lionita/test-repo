package com.example.auction.auction.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.auction.ports.WinningBidLookupPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuctionCommandServiceTest {

    @Test
    void createStoresExtendedAuctionFields() {
        var auctions = new InMemAuctions();
        var outbox = new InMemOutbox();
        var service = new AuctionCommandService(auctions, outbox, new InMemBids());

        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-01-01T12:00:00Z");

        UUID auctionId = service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                start,
                end);

        Auction auction = auctions.findById(auctionId).orElseThrow();
        assertEquals("Vintage Watch", auction.title());
        assertEquals("Restored 1960s mechanical watch", auction.description());
        assertEquals(start, auction.startTime());
        assertEquals(end, auction.endTime());
        assertNull(auction.winningBidId());
        assertTrue(outbox.events.contains("auction.created"));
    }

    @Test
    void createRejectsInvalidTimeWindow() {
        var service = new AuctionCommandService(new InMemAuctions(), new InMemOutbox(), new InMemBids());
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T10:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                start,
                start));
    }

    @Test
    void createRejectsInvalidPricingValues() {
        var service = new AuctionCommandService(new InMemAuctions(), new InMemOutbox(), new InMemBids());
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T10:00:00Z");
        OffsetDateTime end = OffsetDateTime.parse("2026-01-01T12:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                null,
                new BigDecimal("5.00"),
                start,
                end));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                new BigDecimal("-0.01"),
                new BigDecimal("5.00"),
                start,
                end));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                new BigDecimal("100.00"),
                null,
                start,
                end));

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                start,
                end));
    }

    @Test
    void closeSelectsWinnerAndWritesClosedEvent() {
        var auctions = new InMemAuctions();
        var outbox = new InMemOutbox();
        var bids = new InMemBids();
        var service = new AuctionCommandService(auctions, outbox, bids);

        UUID auctionId = UUID.randomUUID();
        UUID bidId = UUID.randomUUID();
        auctions.save(new Auction(auctionId, "Vintage Watch", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), OffsetDateTime.parse("2026-01-01T12:00:00Z"), AuctionStatus.LIVE,
                new BigDecimal("130.00"), null));
        bids.winningByAuction.put(auctionId, new WinningBidLookupPort.WinningBid(bidId, new BigDecimal("130.00"), "bidder-1", 2));

        service.close(auctionId);

        Auction closed = auctions.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.CLOSED, closed.status());
        assertEquals(bidId, closed.winningBidId());
        assertTrue(outbox.events.contains("auction.closed"));
    }


    @Test
    void startRejectsWhenBeforeScheduledStartTime() {
        var auctions = new InMemAuctions();
        var outbox = new InMemOutbox();
        var bids = new InMemBids();
        UUID auctionId = UUID.randomUUID();
        auctions.save(new Auction(auctionId, "Vintage Watch", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), OffsetDateTime.parse("2026-01-01T12:00:00Z"), AuctionStatus.SCHEDULED,
                null, null));

        Clock beforeStart = Clock.fixed(Instant.parse("2026-01-01T09:59:00Z"), ZoneOffset.UTC);
        var service = new AuctionCommandService(auctions, outbox, bids, beforeStart);

        assertThrows(IllegalStateException.class, () -> service.start(auctionId));
        assertTrue(outbox.events.isEmpty());
        assertEquals(AuctionStatus.SCHEDULED, auctions.findById(auctionId).orElseThrow().status());
    }

    @Test
    void closeRejectsWhenBeforeScheduledEndTime() {
        var auctions = new InMemAuctions();
        var outbox = new InMemOutbox();
        var bids = new InMemBids();
        UUID auctionId = UUID.randomUUID();
        auctions.save(new Auction(auctionId, "Vintage Watch", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), OffsetDateTime.parse("2026-01-01T12:00:00Z"), AuctionStatus.LIVE,
                new BigDecimal("130.00"), null));

        Clock beforeEnd = Clock.fixed(Instant.parse("2026-01-01T11:59:00Z"), ZoneOffset.UTC);
        var service = new AuctionCommandService(auctions, outbox, bids, beforeEnd);

        assertThrows(IllegalStateException.class, () -> service.close(auctionId));
        assertTrue(outbox.events.isEmpty());
        assertEquals(AuctionStatus.LIVE, auctions.findById(auctionId).orElseThrow().status());
    }


    @Test
    void closeExpiredAuctions_skipsContendedAuctionAndClosesOthers() {
        var auctions = new InMemAuctions();
        var outbox = new InMemOutbox();
        var bids = new InMemBids();
        var service = new AuctionCommandService(auctions, outbox, bids);

        OffsetDateTime now = OffsetDateTime.parse("2026-01-01T12:00:00Z");
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        auctions.save(new Auction(first, "A1", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                now.minusHours(2), now.minusHours(1), AuctionStatus.LIVE, new BigDecimal("110.00"), null));
        auctions.save(new Auction(second, "A2", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                now.minusHours(2), now.minusMinutes(30), AuctionStatus.LIVE, new BigDecimal("120.00"), null));

        auctions.lockedSnapshots.put(second, new Auction(second, "A2", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                now.minusHours(2), now.minusMinutes(30), AuctionStatus.CLOSED, new BigDecimal("120.00"), UUID.randomUUID()));

        int closedCount = service.closeExpiredAuctions(now);

        assertEquals(1, closedCount);
        assertEquals(AuctionStatus.CLOSED, auctions.findById(first).orElseThrow().status());
        assertEquals(AuctionStatus.LIVE, auctions.findById(second).orElseThrow().status());
        assertEquals(1, outbox.events.stream().filter("auction.closed"::equals).count());
    }

    @Test
    void settleTransitionsClosedAuctionAndWritesSettledEvent() {
        var auctions = new InMemAuctions();
        var outbox = new InMemOutbox();
        var bids = new InMemBids();
        var service = new AuctionCommandService(auctions, outbox, bids);

        UUID auctionId = UUID.randomUUID();
        UUID bidId = UUID.randomUUID();
        auctions.save(new Auction(auctionId, "Vintage Watch", "desc", new BigDecimal("100.00"), new BigDecimal("5.00"),
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), OffsetDateTime.parse("2026-01-01T12:00:00Z"), AuctionStatus.CLOSED,
                new BigDecimal("130.00"), bidId));

        service.settle(auctionId);

        Auction settled = auctions.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.SETTLED, settled.status());
        assertEquals(bidId, settled.winningBidId());
        assertTrue(outbox.events.contains("auction.settled"));

        var settledEvent = outbox.entries.stream()
                .filter(entry -> "auction.settled".equals(entry.eventType()))
                .findFirst()
                .orElseThrow();
        assertEquals("{\"auctionId\":\"" + auctionId + "\",\"winningBidId\":\"" + bidId + "\"}", settledEvent.payload());
    }

    static class InMemAuctions implements AuctionRepositoryPort {
        Map<UUID, Auction> data = new HashMap<>();
        Map<UUID, Auction> lockedSnapshots = new HashMap<>();

        public Auction save(Auction auction) {
            data.put(auction.id(), auction);
            return auction;
        }

        public Optional<Auction> findById(UUID id) {
            return Optional.ofNullable(data.get(id));
        }

        @Override
        public Optional<Auction> findByIdForUpdate(UUID id) {
            Auction locked = lockedSnapshots.get(id);
            if (locked != null) {
                return Optional.of(locked);
            }
            return Optional.ofNullable(data.get(id));
        }

        public List<Auction> findLiveEndingAtOrBefore(OffsetDateTime threshold) {
            return data.values().stream()
                    .filter(auction -> auction.status() == AuctionStatus.LIVE)
                    .filter(auction -> !auction.endTime().isAfter(threshold))
                    .toList();
        }
    }

    static class InMemBids implements WinningBidLookupPort {
        Map<UUID, WinningBid> winningByAuction = new HashMap<>();

        public Optional<WinningBid> findWinningBid(UUID auctionId) {
            return Optional.ofNullable(winningByAuction.get(auctionId));
        }
    }

    static class InMemOutbox implements OutboxPort {
        List<String> events = new ArrayList<>();
        List<OutboxEntry> entries = new ArrayList<>();

        public void append(String eventType, UUID aggregateId, String payload) {
            events.add(eventType);
            entries.add(new OutboxEntry(eventType, payload));
        }

        record OutboxEntry(String eventType, String payload) {}
    }
}
