package com.example.auction.bidding.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.ports.BidRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BiddingCommandServiceTest {
    @Test
    void placesValidBidAndWritesOutbox() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.LIVE, null));

        var service = new BiddingCommandService(auctions, bids, outbox);
        service.placeBid(id, "u1", new BigDecimal("100.00"), "k1");

        assertEquals(new BigDecimal("100.00"), auctions.findById(id).orElseThrow().currentPrice());
        assertEquals(1, bids.seq.get(id));
        assertTrue(outbox.events.contains("bid.placed"));
    }

    @Test
    void rejectsBelowMinimumBid() {
        var auctions = new InMemAuctions();
        var bids = new InMemBids();
        var outbox = new InMemOutbox();
        UUID id = UUID.randomUUID();
        auctions.save(new Auction(id, new BigDecimal("100.00"), new BigDecimal("10.00"), AuctionStatus.LIVE, null));

        var service = new BiddingCommandService(auctions, bids, outbox);
        assertThrows(IllegalArgumentException.class,
                () -> service.placeBid(id, "u1", new BigDecimal("99.99"), "k1"));
    }

    static class InMemAuctions implements AuctionRepositoryPort {
        Map<UUID, Auction> data = new HashMap<>();
        public Auction save(Auction auction) { data.put(auction.id(), auction); return auction; }
        public Optional<Auction> findById(UUID id) { return Optional.ofNullable(data.get(id)); }
    }
    static class InMemBids implements BidRepositoryPort {
        Map<UUID, Long> seq = new HashMap<>();
        public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber) { seq.put(auctionId, sequenceNumber); }
        public long nextSequence(UUID auctionId) { return seq.getOrDefault(auctionId, 0L) + 1; }
    }
    static class InMemOutbox implements OutboxPort {
        List<String> events = new ArrayList<>();
        public void append(String eventType, UUID aggregateId, String payload) { events.add(eventType); }
    }
}
