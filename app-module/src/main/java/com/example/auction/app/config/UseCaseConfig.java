package com.example.auction.app.config;

import com.example.auction.auction.application.AuctionCommandService;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.auction.ports.WinningBidLookupPort;
import com.example.auction.bidding.application.BiddingCommandService;
import com.example.auction.bidding.ports.BidRepositoryPort;
import com.example.auction.bidding.ports.BidderPurchasingAuthorizationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UseCaseConfig {
    @Bean
    Clock appClock() {
        return Clock.systemUTC();
    }

    @Bean
    AuctionCommandService auctionCommandService(AuctionRepositoryPort auctionRepositoryPort,
                                                OutboxPort outboxPort,
                                                BidRepositoryPort bidRepositoryPort,
                                                Clock appClock) {
        WinningBidLookupPort winningBidLookup = auctionId -> bidRepositoryPort.findWinningBid(auctionId)
                .map(w -> new WinningBidLookupPort.WinningBid(w.bidId(), w.amount(), w.bidderId(), w.sequenceNumber()));
        return new AuctionCommandService(auctionRepositoryPort, outboxPort, winningBidLookup, appClock);
    }

    @Bean
    BiddingCommandService biddingCommandService(AuctionRepositoryPort auctionRepositoryPort,
                                                BidRepositoryPort bidRepositoryPort,
                                                OutboxPort outboxPort,
                                                BidderPurchasingAuthorizationPort bidderPurchasingAuthorizationPort,
                                                Clock appClock) {
        return new BiddingCommandService(auctionRepositoryPort, bidRepositoryPort, outboxPort, bidderPurchasingAuthorizationPort, appClock);
    }
}
