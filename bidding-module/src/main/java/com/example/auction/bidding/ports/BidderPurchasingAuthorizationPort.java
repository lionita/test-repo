package com.example.auction.bidding.ports;

import java.math.BigDecimal;

public interface BidderPurchasingAuthorizationPort {
    boolean hasSufficientAuthorization(String bidderId, BigDecimal amount);
}

