package com.example.auction.app.adapters.in.web;

import com.example.auction.app.adapters.out.persistence.BidderJpaEntity;
import com.example.auction.app.application.BidderAdminService;
import com.example.auction.app.security.JwtSubjectValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/bidders")
public class BidderController {
    private final BidderAdminService bidderAdminService;

    public BidderController(BidderAdminService bidderAdminService) {
        this.bidderAdminService = bidderAdminService;
    }

    @PostMapping("/onboarding")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<OnboardBidderResponse> onboardBidder(@AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody OnboardBidderRequest request) {
        JwtSubjectValidator.requireSubject(jwt);
        BidderJpaEntity bidder = bidderAdminService.onboard(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.nationalId(),
                request.purchasingAuthorizationLimit());
        return ResponseEntity.created(URI.create("/api/bidders/" + bidder.getId())).body(toResponse(bidder));
    }

    @PutMapping("/{bidderId}")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<OnboardBidderResponse> updateBidder(@AuthenticationPrincipal Jwt jwt,
                                                              @PathVariable String bidderId,
                                                              @Valid @RequestBody UpdateBidderRequest request) {
        JwtSubjectValidator.requireSubject(jwt);
        BidderJpaEntity bidder = bidderAdminService.update(
                bidderId,
                request.firstName(),
                request.lastName(),
                request.email(),
                request.nationalId(),
                request.purchasingAuthorizationLimit());
        return ResponseEntity.ok(toResponse(bidder));
    }

    @PostMapping("/{bidderId}/block")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<Void> blockBidder(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable String bidderId) {
        JwtSubjectValidator.requireSubject(jwt);
        bidderAdminService.block(bidderId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{bidderId}/unblock")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<Void> unblockBidder(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable String bidderId) {
        JwtSubjectValidator.requireSubject(jwt);
        bidderAdminService.unblock(bidderId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{bidderId}")
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<Void> softDeleteBidder(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable String bidderId) {
        JwtSubjectValidator.requireSubject(jwt);
        bidderAdminService.softDelete(bidderId);
        return ResponseEntity.noContent().build();
    }

    private static OnboardBidderResponse toResponse(BidderJpaEntity bidder) {
        return new OnboardBidderResponse(
                bidder.getId(),
                bidder.getFirstName(),
                bidder.getLastName(),
                bidder.getEmail(),
                bidder.getNationalId(),
                bidder.getPurchasingAuthorizationLimit(),
                bidder.getBlockedUntil());
    }

    public record OnboardBidderRequest(@NotBlank @Size(max = 100) String firstName,
                                       @NotBlank @Size(max = 100) String lastName,
                                       @NotBlank @Email @Size(max = 255) String email,
                                       @NotBlank @Size(max = 50) String nationalId,
                                       @NotNull @DecimalMin("0.01") BigDecimal purchasingAuthorizationLimit) {}

    public record UpdateBidderRequest(@NotBlank @Size(max = 100) String firstName,
                                      @NotBlank @Size(max = 100) String lastName,
                                      @NotBlank @Email @Size(max = 255) String email,
                                      @NotBlank @Size(max = 50) String nationalId,
                                      @NotNull @DecimalMin("0.01") BigDecimal purchasingAuthorizationLimit) {}

    public record OnboardBidderResponse(String bidderId,
                                        String firstName,
                                        String lastName,
                                        String email,
                                        String nationalId,
                                        BigDecimal purchasingAuthorizationLimit,
                                        OffsetDateTime blockedUntil) {}
}
