package com.example.auction.auction.ports;

import java.util.UUID;

public interface OutboxPort {
    void append(String eventType, UUID aggregateId, String payload);
}
