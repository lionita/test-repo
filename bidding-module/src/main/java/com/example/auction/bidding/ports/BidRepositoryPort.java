package com.example.auction.bidding.ports;

import com.example.auction.bidding.domain.BidStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface BidRepositoryPort {
    record WinningBid(UUID bidId, BigDecimal amount, String bidderId, long sequenceNumber) {}
    record BidDecision(BidStatus status, String rejectReason) {}

    void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, Long sequenceNumber, BidStatus bidStatus, String rejectReason);
    Optional<BidDecision> findByAuctionIdAndBidderIdAndIdempotencyKey(UUID auctionId, String bidderId, String idempotencyKey);
    long nextSequence(UUID auctionId);
    Optional<WinningBid> findWinningBid(UUID auctionId);
}
