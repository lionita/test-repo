package com.example.auction.bidding.ports;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface BidRepositoryPort {
    record WinningBid(UUID bidId, BigDecimal amount, String bidderId, long sequenceNumber) {}

    void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber);
    long nextSequence(UUID auctionId);
    Optional<WinningBid> findWinningBid(UUID auctionId);
}
