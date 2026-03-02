package com.example.auction.application;

import com.example.auction.domain.Auction;
import com.example.auction.domain.AuctionStatus;
import com.example.auction.ports.AuctionRepositoryPort;
import com.example.auction.ports.BidRepositoryPort;
import com.example.auction.ports.OutboxPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AuctionService {
    private final AuctionRepositoryPort auctionRepository;
    private final BidRepositoryPort bidRepository;
    private final OutboxPort outboxPort;

    public AuctionService(AuctionRepositoryPort auctionRepository, BidRepositoryPort bidRepository, OutboxPort outboxPort) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.outboxPort = outboxPort;
    }

    @Transactional
    public UUID create(BigDecimal reservePrice, BigDecimal minIncrement) {
        UUID id = UUID.randomUUID();
        Auction auction = new Auction(id, reservePrice, minIncrement, AuctionStatus.SCHEDULED, null);
        auctionRepository.save(auction);
        outboxPort.append("auction.created", id, "{\"auctionId\":\"" + id + "\"}");
        return id;
    }

    @Transactional
    public void start(UUID auctionId) {
        Auction auction = load(auctionId).start();
        auctionRepository.save(auction);
        outboxPort.append("auction.started", auctionId, "{\"auctionId\":\"" + auctionId + "\"}");
    }

    @Transactional
    public void placeBid(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey) {
        Auction auction = load(auctionId);
        if (auction.status() != AuctionStatus.LIVE) {
            throw new IllegalStateException("auction is not live");
        }
        BigDecimal minimum = auction.minimumAllowedBid();
        if (amount.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("bid must be >= " + minimum);
        }

        long sequence = bidRepository.nextSequence(auctionId);
        bidRepository.save(auctionId, bidderId, amount, idempotencyKey, sequence);
        auctionRepository.save(new Auction(auction.id(), auction.reservePrice(), auction.minIncrement(), auction.status(), amount));
        outboxPort.append("bid.placed", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"amount\":" + amount + ",\"sequence\":" + sequence + "}");
    }

    private Auction load(UUID auctionId) {
        return auctionRepository.findById(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
    }
}
