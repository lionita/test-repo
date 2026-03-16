package com.example.auction.app.adapters.in.web;

import com.example.auction.app.adapters.out.persistence.BidderJpaEntity;
import com.example.auction.app.adapters.out.persistence.SpringDataBidderRepository;
import com.example.auction.app.application.BidderAdminService;
import com.example.auction.app.security.JwtSubjectValidator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/bidders")
public class BidderController {
    private final BidderAdminService bidderAdminService;
    private final SpringDataBidderRepository bidderRepository;

    public BidderController(BidderAdminService bidderAdminService,
                            SpringDataBidderRepository bidderRepository) {
        this.bidderAdminService = bidderAdminService;
        this.bidderRepository = bidderRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_admin.write')")
    public ResponseEntity<List<OnboardBidderResponse>> listBidders(@AuthenticationPrincipal Jwt jwt,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "50") int limit,
                                                                   @RequestParam(defaultValue = "createdAt") String sortBy,
                                                                   @RequestParam(defaultValue = "desc") String sortDir) {
        JwtSubjectValidator.requireSubject(jwt);
        int safePage = normalizePage(page);
        int safeLimit = normalizeLimit(limit);
        Sort sort = bidderSort(sortBy, sortDir);
        List<OnboardBidderResponse> bidders = bidderRepository.findByDeletedAtIsNull(PageRequest.of(safePage, safeLimit, sort))
                .stream()
                .map(BidderController::toResponse)
                .toList();
        return ResponseEntity.ok(bidders);
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

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }
        return Math.min(limit, 200);
    }

    private static int normalizePage(int page) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        return page;
    }

    private static Sort bidderSort(String sortBy, String sortDir) {
        String property = normalizeSortProperty(sortBy, Set.of(
                "id", "firstName", "lastName", "email", "nationalId",
                "purchasingAuthorizationLimit", "createdAt", "blockedUntil"));
        Sort.Direction direction = Sort.Direction.fromOptionalString(sortDir)
                .orElseThrow(() -> new IllegalArgumentException("sortDir must be ASC or DESC"));
        return Sort.by(direction, property);
    }

    private static String normalizeSortProperty(String sortBy, Set<String> allowed) {
        if (sortBy == null || sortBy.isBlank()) {
            throw new IllegalArgumentException("sortBy is required");
        }
        String normalized = sortBy.trim();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("unsupported sortBy: " + normalized);
        }
        return normalized;
    }
}
