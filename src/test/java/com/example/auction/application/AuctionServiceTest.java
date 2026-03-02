package com.example.auction.application;

import com.example.auction.domain.Auction;
import com.example.auction.domain.AuctionStatus;
import com.example.auction.ports.AuctionRepositoryPort;
import com.example.auction.ports.BidRepositoryPort;
import com.example.auction.ports.OutboxPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AuctionServiceTest {
    private InMemoryAuctionRepository auctionRepo;
    private InMemoryBidRepository bidRepo;
    private InMemoryOutbox outbox;
    private AuctionService service;

    @BeforeEach
    void setUp() {
        auctionRepo = new InMemoryAuctionRepository();
        bidRepo = new InMemoryBidRepository();
        outbox = new InMemoryOutbox();
        service = new AuctionService(auctionRepo, bidRepo, outbox);
    }

    @Test
    void createStartAndPlaceBidHappyPath() {
        UUID auctionId = service.create(new BigDecimal("100.00"), new BigDecimal("10.00"));
        service.start(auctionId);
        service.placeBid(auctionId, "bidder-1", new BigDecimal("100.00"), "k1");

        Auction saved = auctionRepo.findById(auctionId).orElseThrow();
        assertEquals(AuctionStatus.LIVE, saved.status());
        assertEquals(new BigDecimal("100.00"), saved.currentPrice());
        assertEquals(3, outbox.events.size());
        assertTrue(outbox.events.contains("auction.created"));
        assertTrue(outbox.events.contains("auction.started"));
        assertTrue(outbox.events.contains("bid.placed"));
    }

    @Test
    void rejectsBidBelowMinimum() {
        UUID auctionId = service.create(new BigDecimal("100.00"), new BigDecimal("10.00"));
        service.start(auctionId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.placeBid(auctionId, "bidder-1", new BigDecimal("99.00"), "k1"));

        assertTrue(ex.getMessage().contains(">="));
    }

    static class InMemoryAuctionRepository implements AuctionRepositoryPort {
        private final Map<UUID, Auction> data = new HashMap<>();

        @Override
        public Auction save(Auction auction) {
            data.put(auction.id(), auction);
            return auction;
        }

        @Override
        public Optional<Auction> findById(UUID id) {
            return Optional.ofNullable(data.get(id));
        }
    }

    static class InMemoryBidRepository implements BidRepositoryPort {
        private final Map<UUID, Long> sequences = new HashMap<>();

        @Override
        public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber) {
            sequences.put(auctionId, sequenceNumber);
        }

        @Override
        public long nextSequence(UUID auctionId) {
            return sequences.getOrDefault(auctionId, 0L) + 1;
        }
    }

    static class InMemoryOutbox implements OutboxPort {
        private final List<String> events = new ArrayList<>();

        @Override
        public void append(String eventType, UUID aggregateId, String payload) {
            events.add(eventType);
        }
    }
}
