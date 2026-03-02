package com.example.auction.app.adapters.out.persistence;

import com.example.auction.auction.domain.AuctionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class AuctionJpaEntity {
    @Id
    private UUID id;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reservePrice;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minIncrement;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;
    @Column(precision = 19, scale = 2)
    private BigDecimal currentPrice;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public BigDecimal getReservePrice() { return reservePrice; }
    public void setReservePrice(BigDecimal reservePrice) { this.reservePrice = reservePrice; }
    public BigDecimal getMinIncrement() { return minIncrement; }
    public void setMinIncrement(BigDecimal minIncrement) { this.minIncrement = minIncrement; }
    public AuctionStatus getStatus() { return status; }
    public void setStatus(AuctionStatus status) { this.status = status; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
}
