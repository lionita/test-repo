package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataAuctionRepository extends JpaRepository<AuctionJpaEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AuctionJpaEntity a where a.id = :id")
    Optional<AuctionJpaEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("select a from AuctionJpaEntity a where a.status = com.example.auction.auction.domain.AuctionStatus.LIVE and a.endTime <= :threshold")
    List<AuctionJpaEntity> findLiveEndingAtOrBefore(@Param("threshold") OffsetDateTime threshold, Pageable pageable);

    default List<AuctionJpaEntity> findLiveEndingAtOrBefore(@Param("threshold") OffsetDateTime threshold) {
        return findLiveEndingAtOrBefore(threshold, Pageable.unpaged());
    }

    @Query("""
            select a
            from AuctionJpaEntity a
            where (:status is null or a.status = :status)
              and (:query is null
                   or lower(a.title) like lower(concat('%', :query, '%'))
                   or lower(a.description) like lower(concat('%', :query, '%')))
            """)
    List<AuctionJpaEntity> search(@Param("status") com.example.auction.auction.domain.AuctionStatus status,
                                  @Param("query") String query,
                                  Pageable pageable);
}
