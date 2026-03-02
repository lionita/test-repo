package com.example.auction.auction.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Auction(UUID id, BigDecimal reservePrice, BigDecimal minIncrement, AuctionStatus status, BigDecimal currentPrice) {
    public Auction start() {
        if (status != AuctionStatus.SCHEDULED) throw new IllegalStateException("auction can only start from SCHEDULED");
        return new Auction(id, reservePrice, minIncrement, AuctionStatus.LIVE, currentPrice);
    }
}
