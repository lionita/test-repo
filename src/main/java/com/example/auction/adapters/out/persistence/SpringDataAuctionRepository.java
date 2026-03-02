package com.example.auction.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataAuctionRepository extends JpaRepository<AuctionJpaEntity, UUID> {
}
