package com.example.auction.app.adapters.out.eventbus;

import java.util.UUID;

public interface EventBusPublisher {
    void publish(String eventType, UUID aggregateId, String payload);
}
