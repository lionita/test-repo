package com.example.auction.app.adapters.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataAuctionRepository extends JpaRepository<AuctionJpaEntity, UUID> {
}
