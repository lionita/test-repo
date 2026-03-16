package com.example.auction.app.adapters.out.persistence;

import com.example.auction.app.adapters.out.eventbus.EventBusPublisher;
import com.example.auction.app.adapters.out.realtime.RealtimePushAdapter;
import com.example.auction.auction.domain.Auction;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.domain.BidStatus;
import com.example.auction.bidding.ports.BidRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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
    private static final int LIVE_AUCTION_SCAN_BATCH_SIZE = 500;
    private static final int OUTBOX_PUBLISH_BATCH_SIZE = 200;

    private final SpringDataAuctionRepository auctionRepository;
    private final SpringDataBidRepository bidRepository;
    private final SpringDataOutboxRepository outboxRepository;
    private final EventBusPublisher eventBusPublisher;
    private final RealtimePushAdapter realtimePushAdapter;
    private final int maxPublishAttempts;
    private final int retryDelaySeconds;
    private final MeterRegistry meterRegistry;
    private final Counter outboxPublishSuccessCounter;
    private final Counter outboxPublishFailureCounter;
    private final Counter outboxDeadLetterCounter;

    @Autowired
    public PersistenceAdapters(SpringDataAuctionRepository auctionRepository,
                               SpringDataBidRepository bidRepository,
                               SpringDataOutboxRepository outboxRepository,
                               EventBusPublisher eventBusPublisher,
                               RealtimePushAdapter realtimePushAdapter,
                               MeterRegistry meterRegistry) {
        this(auctionRepository, bidRepository, outboxRepository, eventBusPublisher, realtimePushAdapter, 5, 5, meterRegistry);
    }

    PersistenceAdapters(SpringDataAuctionRepository auctionRepository,
                        SpringDataBidRepository bidRepository,
                        SpringDataOutboxRepository outboxRepository,
                        EventBusPublisher eventBusPublisher,
                        RealtimePushAdapter realtimePushAdapter,
                        int maxPublishAttempts,
                        int retryDelaySeconds) {
        this(auctionRepository, bidRepository, outboxRepository, eventBusPublisher, realtimePushAdapter, maxPublishAttempts, retryDelaySeconds, new SimpleMeterRegistry());
    }

    PersistenceAdapters(SpringDataAuctionRepository auctionRepository,
                        SpringDataBidRepository bidRepository,
                        SpringDataOutboxRepository outboxRepository,
                        EventBusPublisher eventBusPublisher,
                        RealtimePushAdapter realtimePushAdapter,
                        int maxPublishAttempts,
                        int retryDelaySeconds,
                        MeterRegistry meterRegistry) {
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.outboxRepository = outboxRepository;
        this.eventBusPublisher = eventBusPublisher;
        this.realtimePushAdapter = realtimePushAdapter;
        this.maxPublishAttempts = maxPublishAttempts;
        this.retryDelaySeconds = retryDelaySeconds;
        this.meterRegistry = meterRegistry;
        this.outboxPublishSuccessCounter = Counter.builder("auction.outbox.publish.success.total")
                .description("Total successful outbox publishes")
                .register(meterRegistry);
        this.outboxPublishFailureCounter = Counter.builder("auction.outbox.publish.failure.total")
                .description("Total failed outbox publish attempts")
                .register(meterRegistry);
        this.outboxDeadLetterCounter = Counter.builder("auction.outbox.deadletter.total")
                .description("Total outbox events dead-lettered")
                .register(meterRegistry);
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
    public Optional<Auction> findByIdForUpdate(UUID id) {
        return auctionRepository.findByIdForUpdate(id)
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
        return auctionRepository.findLiveEndingAtOrBefore(threshold, PageRequest.of(0, LIVE_AUCTION_SCAN_BATCH_SIZE))
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
    public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, Long sequenceNumber, BidStatus bidStatus, String rejectReason) {
        BidJpaEntity entity = new BidJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAuctionId(auctionId);
        entity.setBidderId(bidderId);
        entity.setAmount(amount);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setSequenceNumber(sequenceNumber);
        entity.setBidStatus(bidStatus);
        entity.setRejectReason(rejectReason);
        entity.setCreatedAt(OffsetDateTime.now());
        bidRepository.save(entity);
    }


    @Override
    public Optional<BidDecision> findByBidderIdAndIdempotencyKey(String bidderId, String idempotencyKey) {
        return bidRepository.findByBidderIdAndIdempotencyKey(bidderId, idempotencyKey)
                .map(b -> new BidDecision(b.getBidStatus(), b.getRejectReason()));
    }

    @Override
    public Optional<WinningBid> findWinningBid(UUID auctionId) {
        return bidRepository.findFirstByAuctionIdAndBidStatusOrderByAmountDescSequenceNumberAsc(auctionId, BidStatus.ACCEPTED)
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
        entity.setPublishAttempts(0);
        entity.setNextAttemptAt(OffsetDateTime.now());
        outboxRepository.save(entity);
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        Timer.Sample sample = Timer.start(meterRegistry);
        OffsetDateTime now = OffsetDateTime.now();
        int processed = 0;
        for (OutboxEventJpaEntity event : outboxRepository.claimReadyToPublish(now, PageRequest.of(0, OUTBOX_PUBLISH_BATCH_SIZE))) {
            processed++;
            if ("bid.placed".equals(event.getEventType()) || "auction.closed".equals(event.getEventType())) {
                try {
                    realtimePushAdapter.publish(event.getEventType(), event.getPayload());
                } catch (RuntimeException ex) {
                    log.warn("Realtime fanout failed for event type={} aggregateId={}", event.getEventType(), event.getAggregateId(), ex);
                }
            }
            try {
                eventBusPublisher.publish(event.getEventType(), event.getAggregateId(), event.getPayload());
                event.setPublishedAt(OffsetDateTime.now());
                event.setLastError(null);
                outboxPublishSuccessCounter.increment();
            } catch (RuntimeException ex) {
                int attempts = event.getPublishAttempts() + 1;
                event.setPublishAttempts(attempts);
                event.setLastError(ex.getMessage());
                outboxPublishFailureCounter.increment();
                if (attempts >= maxPublishAttempts) {
                    event.setDeadLetteredAt(OffsetDateTime.now());
                    outboxDeadLetterCounter.increment();
                    log.error("Dead-lettered outbox event type={} aggregateId={} after {} attempts", event.getEventType(), event.getAggregateId(), attempts, ex);
                } else {
                    event.setNextAttemptAt(OffsetDateTime.now().plusSeconds((long) retryDelaySeconds * attempts));
                    log.warn("Retrying outbox event type={} aggregateId={} attempt={}", event.getEventType(), event.getAggregateId(), attempts, ex);
                }
            }
        }
        sample.stop(Timer.builder("auction.outbox.publish.batch.duration")
                .description("Outbox publish batch duration")
                .tag("processed", processed > 0 ? "yes" : "no")
                .register(meterRegistry));
    }
}
