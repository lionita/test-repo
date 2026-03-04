package com.example.auction.app.adapters.in.web;

import com.example.auction.auction.application.AuctionCommandService;
import com.example.auction.bidding.application.BiddingCommandService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.example.auction.app.security.JwtSubjectValidator;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
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
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Map<String, UUID>> create(@AuthenticationPrincipal Jwt jwt,
                                                     @Valid @RequestBody CreateAuctionRequest request) {
        JwtSubjectValidator.requireSubject(jwt);
        UUID id = auctionService.create(
                request.title(),
                request.description(),
                request.reservePrice(),
                request.minIncrement(),
                request.startTime(),
                request.endTime());
        return ResponseEntity.created(URI.create("/api/auctions/" + id)).body(Map.of("auctionId", id));
    }

    @PostMapping("/{auctionId}/start")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.start(auctionId);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.close(auctionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{auctionId}/bids")
    @PreAuthorize("hasAuthority('SCOPE_bid.write')")
    public ResponseEntity<Void> placeBid(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID auctionId,
                                         @Valid @RequestBody PlaceBidRequest request) {
        JwtSubjectValidator.requireSubject(jwt);
        biddingService.placeBid(auctionId, request.bidderId(), request.amount(), request.idempotencyKey());
        return ResponseEntity.accepted().build();
    }

    public record CreateAuctionRequest(@NotBlank String title,
                                       @NotBlank String description,
                                       @NotNull @DecimalMin("0.01") BigDecimal reservePrice,
                                       @NotNull @DecimalMin("0.01") BigDecimal minIncrement,
                                       @NotNull OffsetDateTime startTime,
                                       @NotNull OffsetDateTime endTime) {}

    public record PlaceBidRequest(@NotBlank String bidderId,
                                  @NotNull @DecimalMin("0.01") BigDecimal amount,
                                  @NotBlank String idempotencyKey) {}
}
