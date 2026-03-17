package com.example.auction.app.adapters.out.eventbus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboundDomainEvent(String eventType,
                                  UUID aggregateId,
                                  String payload,
                                  OffsetDateTime publishedAt) {
}
