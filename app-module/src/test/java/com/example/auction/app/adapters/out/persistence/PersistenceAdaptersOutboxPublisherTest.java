package com.example.auction.app.adapters.out.persistence;

import com.example.auction.app.adapters.out.eventbus.EventBusPublisher;
import com.example.auction.app.adapters.out.realtime.RealtimePushAdapter;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistenceAdaptersOutboxPublisherTest {

    @Test
    void publishPending_marksEventAsPublishedOnSuccessfulEventBusPublish() {
        SpringDataAuctionRepository auctionRepository = mock(SpringDataAuctionRepository.class);
        SpringDataBidRepository bidRepository = mock(SpringDataBidRepository.class);
        SpringDataOutboxRepository outboxRepository = mock(SpringDataOutboxRepository.class);
        EventBusPublisher eventBusPublisher = mock(EventBusPublisher.class);
        RealtimePushAdapter realtimePushAdapter = mock(RealtimePushAdapter.class);

        OutboxEventJpaEntity event = pendingEvent("bid.placed");
        when(outboxRepository.claimReadyToPublish(any()))
                .thenReturn(List.of(event));

        PersistenceAdapters adapters = new PersistenceAdapters(
                auctionRepository,
                bidRepository,
                outboxRepository,
                eventBusPublisher,
                realtimePushAdapter,
                3,
                10);

        adapters.publishPending();

        verify(eventBusPublisher).publish(event.getEventType(), event.getAggregateId(), event.getPayload());
        verify(realtimePushAdapter).publish(event.getEventType(), event.getPayload());
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getDeadLetteredAt()).isNull();
    }

    @Test
    void publishPending_retriesWhenPublishFailsBelowThreshold() {
        SpringDataAuctionRepository auctionRepository = mock(SpringDataAuctionRepository.class);
        SpringDataBidRepository bidRepository = mock(SpringDataBidRepository.class);
        SpringDataOutboxRepository outboxRepository = mock(SpringDataOutboxRepository.class);
        EventBusPublisher eventBusPublisher = mock(EventBusPublisher.class);
        RealtimePushAdapter realtimePushAdapter = mock(RealtimePushAdapter.class);

        OutboxEventJpaEntity event = pendingEvent("auction.created");
        doThrow(new IllegalStateException("broker unavailable"))
                .when(eventBusPublisher)
                .publish(event.getEventType(), event.getAggregateId(), event.getPayload());
        when(outboxRepository.claimReadyToPublish(any()))
                .thenReturn(List.of(event));

        PersistenceAdapters adapters = new PersistenceAdapters(
                auctionRepository,
                bidRepository,
                outboxRepository,
                eventBusPublisher,
                realtimePushAdapter,
                3,
                5);

        OffsetDateTime before = OffsetDateTime.now();
        adapters.publishPending();

        assertThat(event.getPublishAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getDeadLetteredAt()).isNull();
        assertThat(event.getLastError()).isEqualTo("broker unavailable");
        assertThat(event.getNextAttemptAt()).isAfter(before);
        verify(realtimePushAdapter, never()).publish(any(), any());
    }

    @Test
    void publishPending_stillFanoutsRealtimeWhenEventBusPublishFails() {
        SpringDataAuctionRepository auctionRepository = mock(SpringDataAuctionRepository.class);
        SpringDataBidRepository bidRepository = mock(SpringDataBidRepository.class);
        SpringDataOutboxRepository outboxRepository = mock(SpringDataOutboxRepository.class);
        EventBusPublisher eventBusPublisher = mock(EventBusPublisher.class);
        RealtimePushAdapter realtimePushAdapter = mock(RealtimePushAdapter.class);

        OutboxEventJpaEntity event = pendingEvent("bid.placed");
        doThrow(new IllegalStateException("broker unavailable"))
                .when(eventBusPublisher)
                .publish(event.getEventType(), event.getAggregateId(), event.getPayload());
        when(outboxRepository.claimReadyToPublish(any()))
                .thenReturn(List.of(event));

        PersistenceAdapters adapters = new PersistenceAdapters(
                auctionRepository,
                bidRepository,
                outboxRepository,
                eventBusPublisher,
                realtimePushAdapter,
                3,
                5);

        adapters.publishPending();

        verify(realtimePushAdapter).publish(event.getEventType(), event.getPayload());
        assertThat(event.getPublishAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void publishPending_processesLegacyEventWithNullNextAttemptAt() {
        SpringDataAuctionRepository auctionRepository = mock(SpringDataAuctionRepository.class);
        SpringDataBidRepository bidRepository = mock(SpringDataBidRepository.class);
        SpringDataOutboxRepository outboxRepository = mock(SpringDataOutboxRepository.class);
        EventBusPublisher eventBusPublisher = mock(EventBusPublisher.class);
        RealtimePushAdapter realtimePushAdapter = mock(RealtimePushAdapter.class);

        OutboxEventJpaEntity event = pendingEvent("auction.created");
        event.setNextAttemptAt(null);
        when(outboxRepository.claimReadyToPublish(any())).thenReturn(List.of(event));

        PersistenceAdapters adapters = new PersistenceAdapters(
                auctionRepository,
                bidRepository,
                outboxRepository,
                eventBusPublisher,
                realtimePushAdapter,
                3,
                5);

        adapters.publishPending();

        verify(eventBusPublisher).publish(event.getEventType(), event.getAggregateId(), event.getPayload());
        assertThat(event.getPublishedAt()).isNotNull();
    }

    @Test
    void publishPending_deadLettersWhenMaxAttemptsReached() {
        SpringDataAuctionRepository auctionRepository = mock(SpringDataAuctionRepository.class);
        SpringDataBidRepository bidRepository = mock(SpringDataBidRepository.class);
        SpringDataOutboxRepository outboxRepository = mock(SpringDataOutboxRepository.class);
        EventBusPublisher eventBusPublisher = mock(EventBusPublisher.class);
        RealtimePushAdapter realtimePushAdapter = mock(RealtimePushAdapter.class);

        OutboxEventJpaEntity event = pendingEvent("auction.created");
        event.setPublishAttempts(2);
        doThrow(new IllegalStateException("still down"))
                .when(eventBusPublisher)
                .publish(event.getEventType(), event.getAggregateId(), event.getPayload());
        when(outboxRepository.claimReadyToPublish(any()))
                .thenReturn(List.of(event));

        PersistenceAdapters adapters = new PersistenceAdapters(
                auctionRepository,
                bidRepository,
                outboxRepository,
                eventBusPublisher,
                realtimePushAdapter,
                3,
                5);

        adapters.publishPending();

        assertThat(event.getPublishAttempts()).isEqualTo(3);
        assertThat(event.getDeadLetteredAt()).isNotNull();
        assertThat(event.getPublishedAt()).isNull();
    }

    private static OutboxEventJpaEntity pendingEvent(String eventType) {
        OutboxEventJpaEntity event = new OutboxEventJpaEntity();
        event.setId(UUID.randomUUID());
        event.setEventType(eventType);
        event.setAggregateId(UUID.randomUUID());
        event.setPayload("{}");
        event.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        event.setPublishAttempts(0);
        event.setNextAttemptAt(OffsetDateTime.now().minusSeconds(1));
        return event;
    }
}
