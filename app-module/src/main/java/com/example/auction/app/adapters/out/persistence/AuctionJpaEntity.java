package com.example.auction.app.adapters.out.persistence;

import com.example.auction.auction.domain.AuctionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class AuctionJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reservePrice;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minIncrement;
    @Column(nullable = false)
    private OffsetDateTime startTime;
    @Column(nullable = false)
    private OffsetDateTime endTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;
    @Column(precision = 19, scale = 2)
    private BigDecimal currentPrice;
    private UUID winningBidId;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getReservePrice() { return reservePrice; }
    public void setReservePrice(BigDecimal reservePrice) { this.reservePrice = reservePrice; }
    public BigDecimal getMinIncrement() { return minIncrement; }
    public void setMinIncrement(BigDecimal minIncrement) { this.minIncrement = minIncrement; }
    public OffsetDateTime getStartTime() { return startTime; }
    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }
    public OffsetDateTime getEndTime() { return endTime; }
    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }
    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public UUID getWinningBidId() { return winningBidId; }
    public void setWinningBidId(UUID winningBidId) { this.winningBidId = winningBidId; }
}
