package com.example.auction.app.adapters.out.persistence;

import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.ports.BidRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class PersistenceAdapters implements AuctionRepositoryPort, BidRepositoryPort, OutboxPort {
    private static final Logger log = LoggerFactory.getLogger(PersistenceAdapters.class);

    private final SpringDataAuctionRepository auctionRepository;
    private final SpringDataBidRepository bidRepository;
    private final SpringDataOutboxRepository outboxRepository;

    public PersistenceAdapters(SpringDataAuctionRepository auctionRepository,
                               SpringDataBidRepository bidRepository,
                               SpringDataOutboxRepository outboxRepository) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.outboxRepository = outboxRepository;
    }

    @Override
    public Auction save(Auction auction) {
        AuctionJpaEntity entity = new AuctionJpaEntity();
        entity.setId(auction.id());
        entity.setTitle(auction.title());
        entity.setDescription(auction.description());
        entity.setReservePrice(auction.reservePrice());
        entity.setMinIncrement(auction.minIncrement());
        entity.setStartTime(auction.startTime());
        entity.setEndTime(auction.endTime());
        entity.setStatus(auction.status());
        entity.setCurrentPrice(auction.currentPrice());
        entity.setWinningBidId(auction.winningBidId());
        AuctionJpaEntity saved = auctionRepository.save(entity);
        return new Auction(
                saved.getId(),
                saved.getTitle(),
                saved.getDescription(),
                saved.getReservePrice(),
                saved.getMinIncrement(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getStatus(),
                saved.getCurrentPrice(),
                saved.getWinningBidId());
    }

    @Override
    public Optional<Auction> findById(UUID id) {
        return auctionRepository.findById(id)
                .map(e -> new Auction(
                        e.getId(),
                        e.getTitle(),
                        e.getDescription(),
                        e.getReservePrice(),
                        e.getMinIncrement(),
                        e.getStartTime(),
                        e.getEndTime(),
                        e.getStatus(),
                        e.getCurrentPrice(),
                        e.getWinningBidId()));
    }


    @Override
    public List<Auction> findLiveEndingAtOrBefore(OffsetDateTime threshold) {
        return auctionRepository.findLiveEndingAtOrBefore(threshold)
                .stream()
                .map(e -> new Auction(
                        e.getId(),
                        e.getTitle(),
                        e.getDescription(),
                        e.getReservePrice(),
                        e.getMinIncrement(),
                        e.getStartTime(),
                        e.getEndTime(),
                        e.getStatus(),
                        e.getCurrentPrice(),
                        e.getWinningBidId()))
                .toList();
    }

    @Override
    public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber) {
        BidJpaEntity entity = new BidJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAuctionId(auctionId);
        entity.setBidderId(bidderId);
        entity.setAmount(amount);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setSequenceNumber(sequenceNumber);
        entity.setCreatedAt(OffsetDateTime.now());
        bidRepository.save(entity);
    }


    @Override
    public Optional<WinningBid> findWinningBid(UUID auctionId) {
        return bidRepository.findFirstByAuctionIdOrderByAmountDescSequenceNumberAsc(auctionId)
                .map(bid -> new WinningBid(bid.getId(), bid.getAmount(), bid.getBidderId(), bid.getSequenceNumber()));
    }

    @Override
    @Transactional
    public long nextSequence(UUID auctionId) {
        auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + auctionId));
        return bidRepository.maxSequenceByAuctionId(auctionId) + 1;
    }

    @Override
    public void append(String eventType, UUID aggregateId, String payload) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
        entity.setPayload(payload);
        entity.setCreatedAt(OffsetDateTime.now());
        outboxRepository.save(entity);
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        for (OutboxEventJpaEntity event : outboxRepository.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            log.info("Publishing outbox event type={} aggregateId={}", event.getEventType(), event.getAggregateId());
            event.setPublishedAt(OffsetDateTime.now());
        }
    }
}
