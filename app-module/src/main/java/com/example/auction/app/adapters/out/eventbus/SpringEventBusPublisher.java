package com.example.auction.app.adapters.out.eventbus;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class SpringEventBusPublisher implements EventBusPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    public SpringEventBusPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Override
    public void publish(String eventType, UUID aggregateId, String payload) {
        applicationEventPublisher.publishEvent(new OutboundDomainEvent(eventType, aggregateId, payload, OffsetDateTime.now()));
    }
}
