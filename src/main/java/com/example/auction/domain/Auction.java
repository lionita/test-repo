package com.example.auction.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Auction(
        UUID id,
        BigDecimal reservePrice,
        BigDecimal minIncrement,
        AuctionStatus status,
        BigDecimal currentPrice
) {
    public Auction start() {
        if (status != AuctionStatus.SCHEDULED) {
            throw new IllegalStateException("auction can only start from SCHEDULED");
        }
        return new Auction(id, reservePrice, minIncrement, AuctionStatus.LIVE, currentPrice);
    }

    public Auction close() {
        if (status != AuctionStatus.LIVE) {
            throw new IllegalStateException("auction can only close from LIVE");
        }
        return new Auction(id, reservePrice, minIncrement, AuctionStatus.CLOSED, currentPrice);
    }

    public BigDecimal minimumAllowedBid() {
        if (currentPrice == null) {
            return reservePrice;
        }
        return currentPrice.add(minIncrement);
    }
}
