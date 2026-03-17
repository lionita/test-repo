package com.example.auction.app.adapters.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface SpringDataBidderRepository extends JpaRepository<BidderJpaEntity, String> {
    Optional<BidderJpaEntity> findByIdAndDeletedAtIsNull(String id);
    Page<BidderJpaEntity> findByDeletedAtIsNull(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BidderJpaEntity b where b.id = :id")
    Optional<BidderJpaEntity> findByIdForUpdate(String id);
}
