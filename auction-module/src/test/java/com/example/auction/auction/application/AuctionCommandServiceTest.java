package com.example.auction.auction.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.auction.ports.WinningBidLookupPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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
    }

    static class InMemAuctions implements AuctionRepositoryPort {
        Map<UUID, Auction> data = new HashMap<>();

        public Auction save(Auction auction) {
            data.put(auction.id(), auction);
            return auction;
        }

        public Optional<Auction> findById(UUID id) {
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

        public void append(String eventType, UUID aggregateId, String payload) {
            events.add(eventType);
        }
    }
}
