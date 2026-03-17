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

    public Auction schedule() {
        if (status != AuctionStatus.DRAFT) throw new IllegalStateException("auction can only schedule from DRAFT");
        return new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.SCHEDULED, currentPrice, winningBidId);
    }

    public Auction start() {
        if (status != AuctionStatus.SCHEDULED) throw new IllegalStateException("auction can only start from SCHEDULED");
        return new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.LIVE, currentPrice, winningBidId);
    }

    public Auction close(UUID selectedWinningBidId) {
        if (status != AuctionStatus.LIVE) throw new IllegalStateException("auction can only close from LIVE");
        return new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.CLOSED, currentPrice, selectedWinningBidId);
    }

    public Auction settle() {
        if (status != AuctionStatus.CLOSED) throw new IllegalStateException("auction can only settle from CLOSED");
        if (winningBidId == null) throw new IllegalStateException("cannot settle auction without winning bid");
        return new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.SETTLED, currentPrice, winningBidId);
    }

    public Auction cancel() {
        if (status == AuctionStatus.CLOSED || status == AuctionStatus.SETTLED || status == AuctionStatus.CANCELLED) {
            throw new IllegalStateException("auction cannot be cancelled from " + status);
        }
        return new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.CANCELLED, currentPrice, winningBidId);
    }

}
