package com.example.auction.app.config;

import com.example.auction.auction.application.AuctionCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class AuctionClosingScheduler {
    private static final Logger log = LoggerFactory.getLogger(AuctionClosingScheduler.class);

    private final AuctionCommandService auctionCommandService;

    public AuctionClosingScheduler(AuctionCommandService auctionCommandService) {
        this.auctionCommandService = auctionCommandService;
    }

    @Scheduled(fixedDelayString = "${auction.close-scheduler.fixed-delay-ms:10000}")
    public void closeExpiredAuctions() {
        int closedCount = auctionCommandService.closeExpiredAuctions(OffsetDateTime.now());
        if (closedCount > 0) {
            log.info("Closed {} expired auctions", closedCount);
        }
    }
}
