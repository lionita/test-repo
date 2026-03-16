package com.example.auction.app.adapters.out.persistence;

import com.example.auction.bidding.domain.BidStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataBidRepository extends JpaRepository<BidJpaEntity, UUID> {
    @Query("select coalesce(max(b.sequenceNumber),0) from BidJpaEntity b where b.auctionId = :auctionId and b.bidStatus = com.example.auction.bidding.domain.BidStatus.ACCEPTED")
    long maxSequenceByAuctionId(UUID auctionId);
    Optional<BidJpaEntity> findByBidderIdAndIdempotencyKey(String bidderId, String idempotencyKey);
    Optional<BidJpaEntity> findFirstByAuctionIdAndBidStatusOrderByAmountDescSequenceNumberAsc(UUID auctionId, BidStatus bidStatus);
    List<BidJpaEntity> findByAuctionId(UUID auctionId, Pageable pageable);
}
