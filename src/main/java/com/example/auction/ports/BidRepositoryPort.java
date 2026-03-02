package com.example.auction.ports;

import java.math.BigDecimal;
import java.util.UUID;

public interface BidRepositoryPort {
    void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber);
    long nextSequence(UUID auctionId);
}
