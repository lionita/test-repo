package com.example.auction.app.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "bidders", schema = "auction")
public class BidderJpaEntity {
    @Id
    @Column(nullable = false, length = 255)
    private String id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 50)
    private String nationalId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal purchasingAuthorizationLimit;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @Column
    private OffsetDateTime deletedAt;

    @Column
    private OffsetDateTime blockedUntil;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNationalId() { return nationalId; }
    public void setNationalId(String nationalId) { this.nationalId = nationalId; }
    public BigDecimal getPurchasingAuthorizationLimit() { return purchasingAuthorizationLimit; }
    public void setPurchasingAuthorizationLimit(BigDecimal purchasingAuthorizationLimit) { this.purchasingAuthorizationLimit = purchasingAuthorizationLimit; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
    public OffsetDateTime getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(OffsetDateTime blockedUntil) { this.blockedUntil = blockedUntil; }
}
