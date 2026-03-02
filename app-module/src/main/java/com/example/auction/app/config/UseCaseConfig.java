package com.example.auction.app.config;

import com.example.auction.auction.application.AuctionCommandService;
import com.example.auction.auction.ports.AuctionRepositoryPort;
import com.example.auction.auction.ports.OutboxPort;
import com.example.auction.bidding.application.BiddingCommandService;
import com.example.auction.bidding.ports.BidRepositoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {
    @Bean
    AuctionCommandService auctionCommandService(AuctionRepositoryPort auctionRepositoryPort, OutboxPort outboxPort) {
        return new AuctionCommandService(auctionRepositoryPort, outboxPort);
    }

    @Bean
    BiddingCommandService biddingCommandService(AuctionRepositoryPort auctionRepositoryPort,
                                                BidRepositoryPort bidRepositoryPort,
                                                OutboxPort outboxPort) {
        return new BiddingCommandService(auctionRepositoryPort, bidRepositoryPort, outboxPort);
    }
}
