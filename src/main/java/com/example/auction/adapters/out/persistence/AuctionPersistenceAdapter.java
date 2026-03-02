package com.example.auction.adapters.out.persistence;

import com.example.auction.domain.Auction;
import com.example.auction.ports.AuctionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class AuctionPersistenceAdapter implements AuctionRepositoryPort {
    private final SpringDataAuctionRepository repository;

    public AuctionPersistenceAdapter(SpringDataAuctionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Auction save(Auction auction) {
        AuctionJpaEntity entity = new AuctionJpaEntity();
        entity.setId(auction.id());
        entity.setReservePrice(auction.reservePrice());
        entity.setMinIncrement(auction.minIncrement());
        entity.setStatus(auction.status());
        entity.setCurrentPrice(auction.currentPrice());
        AuctionJpaEntity saved = repository.save(entity);
        return new Auction(saved.getId(), saved.getReservePrice(), saved.getMinIncrement(), saved.getStatus(), saved.getCurrentPrice());
    }

    @Override
    public Optional<Auction> findById(UUID id) {
        return repository.findById(id)
                .map(entity -> new Auction(entity.getId(), entity.getReservePrice(), entity.getMinIncrement(), entity.getStatus(), entity.getCurrentPrice()));
    }
}
