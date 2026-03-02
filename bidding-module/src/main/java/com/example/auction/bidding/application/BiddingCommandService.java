package com.example.auction.bidding.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.ports.BidRepositoryPort;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class BiddingCommandService {
    private final AuctionRepositoryPort auctionRepository;
    private final BidRepositoryPort bidRepository;
    private final OutboxPort outboxPort;

    public BiddingCommandService(AuctionRepositoryPort auctionRepository, BidRepositoryPort bidRepository, OutboxPort outboxPort) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.outboxPort = outboxPort;
    }

    public void placeBid(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(auctionId, "auctionId is required");
        if (bidderId == null || bidderId.isBlank()) throw new IllegalArgumentException("bidderId is required");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");

        Auction auction = auctionRepository.findById(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        if (auction.status() != AuctionStatus.LIVE) throw new IllegalStateException("auction is not live");

        BigDecimal minimum = auction.currentPrice() == null ? auction.reservePrice() : auction.currentPrice().add(auction.minIncrement());
        if (amount.compareTo(minimum) < 0) throw new IllegalArgumentException("bid must be >= " + minimum);

        long seq = bidRepository.nextSequence(auctionId);
        bidRepository.save(auctionId, bidderId, amount, idempotencyKey, seq);
        auctionRepository.save(new Auction(auction.id(), auction.reservePrice(), auction.minIncrement(), auction.status(), amount));
        outboxPort.append("bid.placed", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"amount\":" + amount + ",\"sequence\":" + seq + "}");
    }
}
