package com.example.auction.auction.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.auction.ports.WinningBidLookupPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AuctionCommandService {
    private final AuctionRepositoryPort auctionRepository;
    private final OutboxPort outboxPort;
    private final WinningBidLookupPort winningBidLookup;

    public AuctionCommandService(AuctionRepositoryPort auctionRepository, OutboxPort outboxPort, WinningBidLookupPort winningBidLookup) {
        this.auctionRepository = auctionRepository;
        this.outboxPort = outboxPort;
        this.winningBidLookup = winningBidLookup;
    }

    @Transactional
    public UUID create(String title, String description, BigDecimal reservePrice, BigDecimal minIncrement, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (startTime == null) throw new IllegalArgumentException("startTime is required");
        if (endTime == null) throw new IllegalArgumentException("endTime is required");
        if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("endTime must be after startTime");

        UUID id = UUID.randomUUID();
        auctionRepository.save(new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.SCHEDULED, null, null));
        outboxPort.append("auction.created", id, "{\"auctionId\":\"" + id + "\"}");
        return id;
    }

    @Transactional
    public void close(UUID auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        if (auction.status() != AuctionStatus.LIVE) throw new IllegalStateException("auction is not live");

        WinningBidLookupPort.WinningBid winningBid = winningBidLookup.findWinningBid(auctionId).orElse(null);
        Auction closedAuction = auction.close(winningBid == null ? null : winningBid.bidId());
        auctionRepository.save(closedAuction);
        outboxPort.append("auction.closed", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"winningBidId\":" +
                        (winningBid == null ? "null" : "\"" + winningBid.bidId() + "\"") + "}");
    }

    @Transactional
    public int closeExpiredAuctions(OffsetDateTime now) {
        List<Auction> auctionsToClose = auctionRepository.findLiveEndingAtOrBefore(now);
        for (Auction auction : auctionsToClose) {
            close(auction.id());
        }
        return auctionsToClose.size();
    }

    @Transactional
    public void start(UUID auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        auctionRepository.save(auction.start());
        outboxPort.append("auction.started", auctionId, "{\"auctionId\":\"" + auctionId + "\"}");
    }

    @Transactional
    public void settle(UUID auctionId) {
        Auction auction = auctionRepository.findById(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        Auction settledAuction = auction.settle();
        auctionRepository.save(settledAuction);
        outboxPort.append("auction.settled", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"winningBidId\":\"" + settledAuction.winningBidId() + "\"}");
    }
}
