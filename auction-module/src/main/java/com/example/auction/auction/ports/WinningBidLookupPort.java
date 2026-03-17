package com.example.auction.auction.ports;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface WinningBidLookupPort {
    record WinningBid(UUID bidId, BigDecimal amount, String bidderId, long sequenceNumber) {}

    Optional<WinningBid> findWinningBid(UUID auctionId);
}
