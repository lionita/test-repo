package com.example.auction.app.adapters.out.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bids", uniqueConstraints = {
        @UniqueConstraint(name = "uq_bid_auction_sequence", columnNames = {"auctionId", "sequenceNumber"}),
        @UniqueConstraint(name = "uq_bidder_idempotency", columnNames = {"bidderId", "idempotencyKey"})
})
public class BidJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID auctionId;
    @Column(nullable = false)
    private String bidderId;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false)
    private long sequenceNumber;
    @Column(nullable = false)
    private String idempotencyKey;
    @Column(nullable = false)
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAuctionId() { return auctionId; }
    public void setAuctionId(UUID auctionId) { this.auctionId = auctionId; }
    public String getBidderId() { return bidderId; }
    public void setBidderId(String bidderId) { this.bidderId = bidderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
