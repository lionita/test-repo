package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    @Query(value = """
            select *
            from outbox_events
            where published_at is null
              and dead_lettered_at is null
              and (next_attempt_at is null or next_attempt_at <= :now)
            order by created_at asc
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEventJpaEntity> claimReadyToPublish(@Param("now") OffsetDateTime now);
}
