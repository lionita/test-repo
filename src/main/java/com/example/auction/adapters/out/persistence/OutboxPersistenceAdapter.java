package com.example.auction.adapters.out.persistence;

import com.example.auction.ports.OutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class OutboxPersistenceAdapter implements OutboxPort {
    private static final Logger log = LoggerFactory.getLogger(OutboxPersistenceAdapter.class);

    private final SpringDataOutboxRepository repository;

    public OutboxPersistenceAdapter(SpringDataOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(String eventType, UUID aggregateId, String payload) {
        OutboxEventJpaEntity entity = new OutboxEventJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setEventType(eventType);
        entity.setAggregateId(aggregateId);
        entity.setPayload(payload);
        entity.setCreatedAt(OffsetDateTime.now());
        repository.save(entity);
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void publishPending() {
        for (OutboxEventJpaEntity event : repository.findTop20ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            log.info("Publishing outbox event type={} aggregateId={} payload={}", event.getEventType(), event.getAggregateId(), event.getPayload());
            event.setPublishedAt(OffsetDateTime.now());
        }
    }
}
