package com.example.auction.app.adapters.in.web;

import com.example.auction.auction.application.AuctionCommandService;
import com.example.auction.bidding.application.BiddingCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {
    private final AuctionCommandService auctionService;
    private final BiddingCommandService biddingService;

    public AuctionController(AuctionCommandService auctionService, BiddingCommandService biddingService) {
        this.auctionService = auctionService;
        this.biddingService = biddingService;
    }

    @PostMapping
    public ResponseEntity<Map<String, UUID>> create(@Valid @RequestBody CreateAuctionRequest request) {
        UUID id = auctionService.create(request.reservePrice(), request.minIncrement());
        return ResponseEntity.created(URI.create("/api/auctions/" + id)).body(Map.of("auctionId", id));
    }

    @PostMapping("/{auctionId}/start")
    public ResponseEntity<Void> start(@PathVariable UUID auctionId) {
        auctionService.start(auctionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{auctionId}/bids")
    public ResponseEntity<Void> placeBid(@PathVariable UUID auctionId, @Valid @RequestBody PlaceBidRequest request) {
        biddingService.placeBid(auctionId, request.bidderId(), request.amount(), request.idempotencyKey());
        return ResponseEntity.accepted().build();
    }

    public record CreateAuctionRequest(@NotNull @DecimalMin("0.01") BigDecimal reservePrice,
                                       @NotNull @DecimalMin("0.01") BigDecimal minIncrement) {}

    public record PlaceBidRequest(@NotBlank String bidderId,
                                  @NotNull @DecimalMin("0.01") BigDecimal amount,
                                  @NotBlank String idempotencyKey) {}
}
