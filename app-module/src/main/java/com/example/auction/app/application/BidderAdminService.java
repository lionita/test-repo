package com.example.auction.app.application;

import com.example.auction.app.adapters.out.persistence.BidderJpaEntity;
import com.example.auction.app.adapters.out.persistence.SpringDataBidderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class BidderAdminService {
    private final SpringDataBidderRepository bidderRepository;
    private final int blockDurationMonths;

    public BidderAdminService(SpringDataBidderRepository bidderRepository,
                              @Value("${auction.bidders.block-duration-months:2}") int blockDurationMonths) {
        this.bidderRepository = bidderRepository;
        this.blockDurationMonths = blockDurationMonths;
    }

    public BidderJpaEntity onboard(String firstName,
                                   String lastName,
                                   String email,
                                   String nationalId,
                                   BigDecimal purchasingAuthorizationLimit) {
        String bidderId = UUID.randomUUID().toString();
        BidderJpaEntity bidder = new BidderJpaEntity();
        bidder.setId(bidderId);
        bidder.setFirstName(firstName);
        bidder.setLastName(lastName);
        bidder.setEmail(email);
        bidder.setNationalId(nationalId);
        bidder.setPurchasingAuthorizationLimit(purchasingAuthorizationLimit);
        bidder.setCreatedAt(OffsetDateTime.now());
        return bidderRepository.save(bidder);
    }

    public BidderJpaEntity update(String bidderId,
                                  String firstName,
                                  String lastName,
                                  String email,
                                  String nationalId,
                                  BigDecimal purchasingAuthorizationLimit) {
        BidderJpaEntity bidder = findActiveBidder(bidderId);
        bidder.setFirstName(firstName);
        bidder.setLastName(lastName);
        bidder.setEmail(email);
        bidder.setNationalId(nationalId);
        bidder.setPurchasingAuthorizationLimit(purchasingAuthorizationLimit);
        return bidderRepository.save(bidder);
    }

    public void block(String bidderId) {
        BidderJpaEntity bidder = findActiveBidder(bidderId);
        bidder.setBlockedUntil(OffsetDateTime.now().plusMonths(blockDurationMonths));
        bidderRepository.save(bidder);
    }

    public void unblock(String bidderId) {
        BidderJpaEntity bidder = findActiveBidder(bidderId);
        bidder.setBlockedUntil(null);
        bidderRepository.save(bidder);
    }

    public void softDelete(String bidderId) {
        BidderJpaEntity bidder = bidderRepository.findById(bidderId)
                .orElseThrow(() -> new IllegalArgumentException("bidder not found: " + bidderId));
        if (bidder.getDeletedAt() == null) {
            bidder.setDeletedAt(OffsetDateTime.now());
            bidderRepository.save(bidder);
        }
    }

    private BidderJpaEntity findActiveBidder(String bidderId) {
        BidderJpaEntity bidder = bidderRepository.findById(bidderId)
                .orElseThrow(() -> new IllegalArgumentException("bidder not found: " + bidderId));
        if (bidder.getDeletedAt() != null) {
            throw new IllegalArgumentException("bidder is deleted: " + bidderId);
        }
        return bidder;
    }
}
