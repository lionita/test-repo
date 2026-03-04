package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataBidRepository extends JpaRepository<BidJpaEntity, UUID> {
    @Query("select coalesce(max(b.sequenceNumber),0) from BidJpaEntity b where b.auctionId = :auctionId")
    long maxSequenceByAuctionId(UUID auctionId);
    Optional<BidJpaEntity> findFirstByAuctionIdOrderByAmountDescSequenceNumberAsc(UUID auctionId);
}
