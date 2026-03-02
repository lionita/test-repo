package com.example.auction.auction.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

public class AuctionCommandService {
    private final AuctionRepositoryPort auctionRepository;
    private final OutboxPort outboxPort;

    public AuctionCommandService(AuctionRepositoryPort auctionRepository, OutboxPort outboxPort) {
        this.auctionRepository = auctionRepository;
        this.outboxPort = outboxPort;
    }

    @Transactional
    public UUID create(BigDecimal reservePrice, BigDecimal minIncrement) {
        UUID id = UUID.randomUUID();
        auctionRepository.save(new Auction(id, reservePrice, minIncrement, AuctionStatus.SCHEDULED, null));
        outboxPort.append("auction.created", id, "{\"auctionId\":\"" + id + "\"}");
        return id;
    }

    @Transactional
    public void start(UUID auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        auctionRepository.save(auction.start());
        outboxPort.append("auction.started", auctionId, "{\"auctionId\":\"" + auctionId + "\"}");
    }
}
