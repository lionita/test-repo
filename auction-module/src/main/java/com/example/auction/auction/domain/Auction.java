package com.example.auction.auction.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Auction(
        UUID id,
        String title,
        String description,
        BigDecimal reservePrice,
        BigDecimal minIncrement,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        AuctionStatus status,
        BigDecimal currentPrice,
        UUID winningBidId) {

    public Auction start() {
        if (status != AuctionStatus.SCHEDULED) throw new IllegalStateException("auction can only start from SCHEDULED");
        return new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.LIVE, currentPrice, winningBidId);
    }
}
