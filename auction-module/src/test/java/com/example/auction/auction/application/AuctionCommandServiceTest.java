package com.example.auction.auction.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
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
        var service = new AuctionCommandService(auctions, outbox);

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
        var service = new AuctionCommandService(new InMemAuctions(), new InMemOutbox());
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T10:00:00Z");

        assertThrows(IllegalArgumentException.class, () -> service.create(
                "Vintage Watch",
                "Restored 1960s mechanical watch",
                new BigDecimal("100.00"),
                new BigDecimal("5.00"),
                start,
                start));
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
    }

    static class InMemOutbox implements OutboxPort {
        List<String> events = new ArrayList<>();

        public void append(String eventType, UUID aggregateId, String payload) {
            events.add(eventType);
        }
    }
}
