package com.example.auction.app.adapters.out.authorization;

import com.example.auction.bidding.ports.BidderPurchasingAuthorizationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class StaticBidderPurchasingAuthorizationAdapter implements BidderPurchasingAuthorizationPort {
    private final BigDecimal defaultAuthorizationLimit;

    public StaticBidderPurchasingAuthorizationAdapter(@Value("${auction.bidding.default-purchasing-authorization-limit:1000000}") BigDecimal defaultAuthorizationLimit) {
        this.defaultAuthorizationLimit = defaultAuthorizationLimit;
    }

    @Override
    public boolean hasSufficientAuthorization(String bidderId, BigDecimal amount) {
        return amount.compareTo(defaultAuthorizationLimit) <= 0;
    }
}
