package com.example.auction.adapters.out.persistence;

import com.example.auction.ports.BidRepositoryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class BidPersistenceAdapter implements BidRepositoryPort {
    private final SpringDataBidRepository repository;

    public BidPersistenceAdapter(SpringDataBidRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(UUID auctionId, String bidderId, BigDecimal amount, String idempotencyKey, long sequenceNumber) {
        BidJpaEntity entity = new BidJpaEntity();
        entity.setId(UUID.randomUUID());
        entity.setAuctionId(auctionId);
        entity.setBidderId(bidderId);
        entity.setAmount(amount);
        entity.setIdempotencyKey(idempotencyKey);
        entity.setSequenceNumber(sequenceNumber);
        entity.setCreatedAt(OffsetDateTime.now());
        repository.save(entity);
    }

    @Override
    public long nextSequence(UUID auctionId) {
        return repository.maxSequenceByAuctionId(auctionId) + 1;
    }
}
