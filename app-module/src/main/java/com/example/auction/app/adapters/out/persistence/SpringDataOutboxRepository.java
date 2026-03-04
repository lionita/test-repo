package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SpringDataOutboxRepository extends JpaRepository<OutboxEventJpaEntity, UUID> {
    @Query("""
            select e
            from OutboxEventJpaEntity e
            where e.publishedAt is null
              and e.deadLetteredAt is null
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            order by e.createdAt asc
            """)
    List<OutboxEventJpaEntity> findReadyToPublish(@Param("now") OffsetDateTime now);
}
