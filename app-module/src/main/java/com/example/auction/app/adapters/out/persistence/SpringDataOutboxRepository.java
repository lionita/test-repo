package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    List<OutboxEventJpaEntity> findTop20ByPublishedAtIsNullAndDeadLetteredAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(OffsetDateTime now);
}
