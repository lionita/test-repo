package com.example.auction.bidding.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.domain.BidStatus;
import com.example.auction.bidding.ports.BidRepositoryPort;
import com.example.auction.bidding.ports.BidderPurchasingAuthorizationPort;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class BiddingCommandService {
    public record BidPlacementResult(BidStatus status, String rejectReason) {}

    private final AuctionRepositoryPort auctionRepository;
    private final BidRepositoryPort bidRepository;
    private final OutboxPort outboxPort;
    private final BidderPurchasingAuthorizationPort bidderPurchasingAuthorizationPort;
    private final Clock clock;

    public BiddingCommandService(AuctionRepositoryPort auctionRepository,
                                 BidRepositoryPort bidRepository,
                                 OutboxPort outboxPort,
                                 BidderPurchasingAuthorizationPort bidderPurchasingAuthorizationPort) {
        this(auctionRepository, bidRepository, outboxPort, bidderPurchasingAuthorizationPort, Clock.systemUTC());
    }

    public BiddingCommandService(AuctionRepositoryPort auctionRepository,
                                 BidRepositoryPort bidRepository,
                                 OutboxPort outboxPort,
                                 BidderPurchasingAuthorizationPort bidderPurchasingAuthorizationPort,
                                 Clock clock) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.outboxPort = outboxPort;
        this.bidderPurchasingAuthorizationPort = bidderPurchasingAuthorizationPort;
        this.clock = clock;
    }

    @Transactional
    public BidPlacementResult placeBid(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey) {
        Objects.requireNonNull(auctionId, "auctionId is required");
        if (bidderId == null || bidderId.isBlank()) throw new IllegalArgumentException("bidderId is required");
        Objects.requireNonNull(amount, "amount is required");
        if (amount.signum() <= 0) throw new IllegalArgumentException("amount must be > 0");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey is required");

        var existing = bidRepository.findByBidderIdAndIdempotencyKey(bidderId, idempotencyKey);
        if (existing.isPresent()) {
            return new BidPlacementResult(existing.get().status(), existing.get().rejectReason());
        }

        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        if (auction.status() != AuctionStatus.LIVE) {
            return reject(auctionId, bidderId, amount, idempotencyKey, "auction is not live");
        }
        if (OffsetDateTime.now(clock).isAfter(auction.endTime())) {
            return reject(auctionId, bidderId, amount, idempotencyKey, "auction has already ended");
        }

        BigDecimal minimum = auction.currentPrice() == null ? auction.reservePrice() : auction.currentPrice().add(auction.minIncrement());
        if (amount.compareTo(minimum) < 0) {
            return reject(auctionId, bidderId, amount, idempotencyKey, "bid must be >= " + minimum);
        }

        if (!bidderPurchasingAuthorizationPort.hasSufficientAuthorization(bidderId, amount)) {
            return reject(auctionId, bidderId, amount, idempotencyKey, "bidder has insufficient purchasing authorization");
        }

        long seq = bidRepository.nextSequence(auctionId);
        bidRepository.save(auctionId, bidderId, amount, idempotencyKey, seq, BidStatus.ACCEPTED, null);
        auctionRepository.save(new Auction(
                auction.id(),
                auction.title(),
                auction.description(),
                auction.reservePrice(),
                auction.minIncrement(),
                auction.startTime(),
                auction.endTime(),
                auction.status(),
                amount,
                auction.winningBidId()));
        outboxPort.append("bid.placed", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"amount\":" + amount + ",\"sequence\":" + seq + "}");
        return new BidPlacementResult(BidStatus.ACCEPTED, null);
    }

    private BidPlacementResult reject(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, String rejectReason) {
        bidRepository.save(auctionId, bidderId, amount, idempotencyKey, null, BidStatus.REJECTED, rejectReason);
        outboxPort.append("bid.rejected", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"amount\":" + amount + ",\"bidderId\":\"" + bidderId + "\",\"reason\":\"" + rejectReason + "\"}");
        return new BidPlacementResult(BidStatus.REJECTED, rejectReason);
    }
}
