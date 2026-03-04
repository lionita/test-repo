package com.example.auction.app.adapters.out.persistence;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String eventType;
    @Column(nullable = false)
    private UUID aggregateId;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false)
    private OffsetDateTime createdAt;
    @Column
    private OffsetDateTime publishedAt;
    @Column(nullable = false)
    private int publishAttempts;
    @Column(nullable = false)
    private OffsetDateTime nextAttemptAt;
    @Column
    private OffsetDateTime deadLetteredAt;
    @Column(columnDefinition = "TEXT")
    private String lastError;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(OffsetDateTime publishedAt) { this.publishedAt = publishedAt; }
    public int getPublishAttempts() { return publishAttempts; }
    public void setPublishAttempts(int publishAttempts) { this.publishAttempts = publishAttempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public OffsetDateTime getDeadLetteredAt() { return deadLetteredAt; }
    public void setDeadLetteredAt(OffsetDateTime deadLetteredAt) { this.deadLetteredAt = deadLetteredAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
