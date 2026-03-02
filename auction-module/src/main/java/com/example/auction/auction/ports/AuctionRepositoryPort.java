package com.example.auction.auction.ports;

import com.example.auction.auction.domain.Auction;

import java.util.Optional;
import java.util.UUID;

public interface AuctionRepositoryPort {
    Auction save(Auction auction);
    Optional<Auction> findById(UUID id);
}
