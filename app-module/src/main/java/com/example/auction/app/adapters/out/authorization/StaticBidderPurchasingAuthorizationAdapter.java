package com.example.auction.app.adapters.out.authorization;

import com.example.auction.app.adapters.out.persistence.SpringDataBidderRepository;
import com.example.auction.bidding.ports.BidderPurchasingAuthorizationPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Component
public class StaticBidderPurchasingAuthorizationAdapter implements BidderPurchasingAuthorizationPort {
    private final SpringDataBidderRepository bidderRepository;

    public StaticBidderPurchasingAuthorizationAdapter(SpringDataBidderRepository bidderRepository) {
        this.bidderRepository = bidderRepository;
    }

    @Override
    public boolean hasSufficientAuthorization(String bidderId, BigDecimal amount) {
        OffsetDateTime now = OffsetDateTime.now();
        return bidderRepository.findByIdAndDeletedAtIsNull(bidderId)
                .filter(b -> b.getBlockedUntil() == null || b.getBlockedUntil().isBefore(now))
                .map(b -> amount.compareTo(b.getPurchasingAuthorizationLimit()) <= 0)
                .orElse(false);
    }
}
