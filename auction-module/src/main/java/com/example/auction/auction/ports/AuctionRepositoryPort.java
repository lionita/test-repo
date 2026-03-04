package com.example.auction.auction.ports;

import com.example.auction.auction.domain.Auction;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuctionRepositoryPort {
    Auction save(Auction auction);
    Optional<Auction> findById(UUID id);
    default Optional<Auction> findByIdForUpdate(UUID id) {
        return findById(id);
    }
    List<Auction> findLiveEndingAtOrBefore(OffsetDateTime threshold);
}
