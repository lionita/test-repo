package com.example.auction.auction.application;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.auction.ports.WinningBidLookupPort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class AuctionCommandService {
    private final AuctionRepositoryPort auctionRepository;
    private final OutboxPort outboxPort;
    private final WinningBidLookupPort winningBidLookup;
    private final Clock clock;

    public AuctionCommandService(AuctionRepositoryPort auctionRepository,
                                 OutboxPort outboxPort,
                                 WinningBidLookupPort winningBidLookup) {
        this(auctionRepository, outboxPort, winningBidLookup, Clock.systemUTC());
    }

    public AuctionCommandService(AuctionRepositoryPort auctionRepository,
                                 OutboxPort outboxPort,
                                 WinningBidLookupPort winningBidLookup,
                                 Clock clock) {
        this.auctionRepository = auctionRepository;
        this.outboxPort = outboxPort;
        this.winningBidLookup = winningBidLookup;
        this.clock = clock;
    }

    @Transactional
    public UUID create(String title, String description, BigDecimal reservePrice, BigDecimal minIncrement, OffsetDateTime startTime, OffsetDateTime endTime) {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("title is required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");
        if (reservePrice == null) throw new IllegalArgumentException("reservePrice is required");
        if (reservePrice.signum() < 0) throw new IllegalArgumentException("reservePrice must be >= 0");
        if (minIncrement == null) throw new IllegalArgumentException("minIncrement is required");
        if (minIncrement.signum() <= 0) throw new IllegalArgumentException("minIncrement must be > 0");
        if (startTime == null) throw new IllegalArgumentException("startTime is required");
        if (endTime == null) throw new IllegalArgumentException("endTime is required");
        if (!endTime.isAfter(startTime)) throw new IllegalArgumentException("endTime must be after startTime");

        UUID id = UUID.randomUUID();
        auctionRepository.save(new Auction(id, title, description, reservePrice, minIncrement, startTime, endTime, AuctionStatus.DRAFT, null, null));
        outboxPort.append("auction.created", id, "{\"auctionId\":\"" + id + "\"}");
        return id;
    }

    @Transactional
    public void schedule(UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        Auction scheduledAuction = auction.schedule();
        auctionRepository.save(scheduledAuction);
        outboxPort.append("auction.scheduled", auctionId, "{\"auctionId\":\"" + auctionId + "\"}");
    }

    @Transactional
    public void close(UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        if (auction.status() != AuctionStatus.LIVE) throw new IllegalStateException("auction is not live");
        if (OffsetDateTime.now(clock).isBefore(auction.endTime())) {
            throw new IllegalStateException("auction cannot be closed before endTime");
        }

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
        int closedCount = 0;
        for (Auction auction : auctionsToClose) {
            if (tryCloseIfLive(auction.id())) {
                closedCount++;
            }
        }
        return closedCount;
    }

    private boolean tryCloseIfLive(UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        if (auction.status() != AuctionStatus.LIVE) {
            return false;
        }

        WinningBidLookupPort.WinningBid winningBid = winningBidLookup.findWinningBid(auctionId).orElse(null);
        Auction closedAuction = auction.close(winningBid == null ? null : winningBid.bidId());
        auctionRepository.save(closedAuction);
        outboxPort.append("auction.closed", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"winningBidId\":" +
                        (winningBid == null ? "null" : "\"" + winningBid.bidId() + "\"") + "}");
        return true;
    }

    @Transactional
    public void start(UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (now.isBefore(auction.startTime())) {
            throw new IllegalStateException("auction cannot be started before startTime");
        }
        if (now.isAfter(auction.endTime())) {
            throw new IllegalStateException("auction cannot be started after endTime");
        }
        auctionRepository.save(auction.start());
        outboxPort.append("auction.started", auctionId, "{\"auctionId\":\"" + auctionId + "\"}");
    }

    @Transactional
    public void settle(UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId).orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        Auction settledAuction = auction.settle();
        auctionRepository.save(settledAuction);
        String winningBidIdJsonValue = settledAuction.winningBidId() == null
                ? "null"
                : "\"" + settledAuction.winningBidId() + "\"";
        outboxPort.append("auction.settled", auctionId,
                "{\"auctionId\":\"" + auctionId + "\",\"winningBidId\":" + winningBidIdJsonValue + "}");
    }

    @Transactional
    public void cancel(UUID auctionId) {
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        Auction cancelledAuction = auction.cancel();
        auctionRepository.save(cancelledAuction);
        outboxPort.append("auction.cancelled", auctionId, "{\"auctionId\":\"" + auctionId + "\"}");
    }
}
