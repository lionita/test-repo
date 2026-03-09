package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataBidderRepository extends JpaRepository<BidderJpaEntity, String> {
    Optional<BidderJpaEntity> findByIdAndDeletedAtIsNull(String id);
}
