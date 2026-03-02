package com.example.auction.ports;

import com.example.auction.domain.Auction;

import java.util.Optional;
import java.util.UUID;

public interface AuctionRepositoryPort {
    Auction save(Auction auction);
    Optional<Auction> findById(UUID id);
}
