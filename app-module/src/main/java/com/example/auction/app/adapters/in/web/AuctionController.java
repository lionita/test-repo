package com.example.auction.app.adapters.in.web;

import com.example.auction.app.adapters.out.persistence.AuctionJpaEntity;
import com.example.auction.app.adapters.out.persistence.BidJpaEntity;
import com.example.auction.app.adapters.out.persistence.SpringDataAuctionRepository;
import com.example.auction.app.adapters.out.persistence.SpringDataBidRepository;
import com.example.auction.app.security.JwtSubjectValidator;
import com.example.auction.auction.application.AuctionCommandService;
import com.example.auction.auction.domain.AuctionStatus;
import com.example.auction.bidding.domain.BidStatus;
import com.example.auction.bidding.application.BiddingCommandService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {
    private final AuctionCommandService auctionService;
    private final BiddingCommandService biddingService;
    private final SpringDataAuctionRepository auctionRepository;
    private final SpringDataBidRepository bidRepository;
    private final MeterRegistry meterRegistry;
    private final Counter acceptedBidsCounter;
    private final Counter rejectedBidsCounter;
    private final Counter bidRequestErrorsCounter;

    public AuctionController(AuctionCommandService auctionService,
                             BiddingCommandService biddingService,
                             SpringDataAuctionRepository auctionRepository,
                             SpringDataBidRepository bidRepository,
                             MeterRegistry meterRegistry) {
        this.auctionService = auctionService;
        this.biddingService = biddingService;
        this.auctionRepository = auctionRepository;
        this.bidRepository = bidRepository;
        this.meterRegistry = meterRegistry;
        this.acceptedBidsCounter = Counter.builder("auction.bids.accepted.total")
                .description("Total accepted bid requests")
                .register(meterRegistry);
        this.rejectedBidsCounter = Counter.builder("auction.bids.rejected.total")
                .description("Total rejected bid requests")
                .register(meterRegistry);
        this.bidRequestErrorsCounter = Counter.builder("auction.bids.errors.total")
                .description("Total bid request errors (exceptions)")
                .register(meterRegistry);
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

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<AuctionResponse> update(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable UUID id,
                                                  @Valid @RequestBody UpdateAuctionRequest request) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.update(
                id,
                request.title(),
                request.description(),
                request.reservePrice(),
                request.minIncrement(),
                request.startTime(),
                request.endTime());
        AuctionJpaEntity updated = auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + id));
        return ResponseEntity.ok(toAuctionResponse(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuctionResponse> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        JwtSubjectValidator.requireSubject(jwt);
        AuctionJpaEntity auction = auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + id));
        return ResponseEntity.ok(toAuctionResponse(auction));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<AuctionResponse>> list(@AuthenticationPrincipal Jwt jwt,
                                                               @RequestParam(required = false) AuctionStatus status,
                                                               @RequestParam(required = false) String query,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "startTime") String sortBy,
                                                               @RequestParam(defaultValue = "desc") String sortDir,
                                                               @RequestParam(defaultValue = "50") int limit) {
        JwtSubjectValidator.requireSubject(jwt);
        int safePage = normalizePage(page);
        int safeLimit = normalizeLimit(limit);
        Sort sort = auctionSort(sortBy, sortDir);
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
        Page<AuctionResponse> result = auctionRepository.search(status, normalizedQuery, PageRequest.of(safePage, safeLimit, sort))
                .map(AuctionController::toAuctionResponse);
        return ResponseEntity.ok(PagedResponse.from(result));
    }

    @PostMapping("/{auctionId}/start")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.start(auctionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{auctionId}/schedule")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> schedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.schedule(auctionId);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/{auctionId}/close")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> close(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.close(auctionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{auctionId}/settle")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> settle(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.settle(auctionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{auctionId}/cancel")
    @PreAuthorize("hasAuthority('SCOPE_auction.write')")
    public ResponseEntity<Void> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID auctionId) {
        JwtSubjectValidator.requireSubject(jwt);
        auctionService.cancel(auctionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{auctionId}/bids")
    @PreAuthorize("hasAuthority('SCOPE_bid.write')")
    public ResponseEntity<PlaceBidResponse> placeBid(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID auctionId,
                                                     @Valid @RequestBody PlaceBidRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "error";
        String subject = JwtSubjectValidator.requireSubject(jwt);
        if (!subject.equals(request.bidderId())) {
            throw new IllegalArgumentException("bidderId must match jwt subject");
        }
        try {
            BiddingCommandService.BidPlacementResult result =
                    biddingService.placeBid(auctionId, request.bidderId(), request.amount(), request.idempotencyKey());
            PlaceBidResponse response = new PlaceBidResponse(result.status().name(), result.rejectReason());
            if (result.status() == BidStatus.ACCEPTED) {
                acceptedBidsCounter.increment();
                outcome = "accepted";
                return ResponseEntity.accepted().body(response);
            }
            rejectedBidsCounter.increment();
            outcome = "rejected";
            return ResponseEntity.status(409).body(response);
        } catch (RuntimeException ex) {
            bidRequestErrorsCounter.increment();
            throw ex;
        } finally {
            sample.stop(Timer.builder("auction.bids.request.duration")
                    .description("Bid request processing time")
                    .tag("outcome", outcome)
                    .register(meterRegistry));
        }
    }

    @GetMapping("/{id}/bids")
    public ResponseEntity<PagedResponse<BidResponse>> listBids(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID id,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "createdAt") String sortBy,
                                                               @RequestParam(defaultValue = "desc") String sortDir,
                                                               @RequestParam(defaultValue = "50") int limit) {
        JwtSubjectValidator.requireSubject(jwt);
        int safePage = normalizePage(page);
        int safeLimit = normalizeLimit(limit);
        Sort sort = bidSort(sortBy, sortDir);
        if (!auctionRepository.existsById(id)) {
            throw new IllegalArgumentException("auction not found: " + id);
        }
        Page<BidResponse> bids = bidRepository.findByAuctionId(id, PageRequest.of(safePage, safeLimit, sort))
                .map(AuctionController::toBidResponse);
        return ResponseEntity.ok(PagedResponse.from(bids));
    }

    @GetMapping("/{id}/result")
    public ResponseEntity<AuctionResultResponse> result(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        JwtSubjectValidator.requireSubject(jwt);
        AuctionJpaEntity auction = auctionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("auction not found: " + id));

        if (auction.getStatus() == AuctionStatus.DRAFT
                || auction.getStatus() == AuctionStatus.SCHEDULED
                || auction.getStatus() == AuctionStatus.LIVE) {
            return ResponseEntity.status(409).body(new AuctionResultResponse(
                    auction.getId(),
                    auction.getStatus().name(),
                    null,
                    null,
                    null,
                    "result not available while auction is " + auction.getStatus().name()
            ));
        }

        if (auction.getStatus() == AuctionStatus.CANCELLED) {
            return ResponseEntity.ok(new AuctionResultResponse(
                    auction.getId(),
                    auction.getStatus().name(),
                    null,
                    null,
                    null,
                    "auction was cancelled"
            ));
        }

        UUID winningBidId = auction.getWinningBidId();
        if (winningBidId == null) {
            return ResponseEntity.ok(new AuctionResultResponse(
                    auction.getId(),
                    auction.getStatus().name(),
                    null,
                    null,
                    null,
                    "auction closed without winner"
            ));
        }

        BidJpaEntity winningBid = bidRepository.findById(winningBidId)
                .orElseThrow(() -> new IllegalStateException("winning bid not found: " + winningBidId));
        return ResponseEntity.ok(new AuctionResultResponse(
                auction.getId(),
                auction.getStatus().name(),
                winningBid.getId(),
                winningBid.getBidderId(),
                winningBid.getAmount(),
                null
        ));
    }

    public record CreateAuctionRequest(@NotBlank @Size(max = 255) String title,
                                       @NotBlank @Size(max = 4000) String description,
                                       @NotNull @DecimalMin("0.01") BigDecimal reservePrice,
                                       @NotNull @DecimalMin("0.01") BigDecimal minIncrement,
                                       @NotNull OffsetDateTime startTime,
                                       @NotNull OffsetDateTime endTime) {}

    public record UpdateAuctionRequest(@NotBlank @Size(max = 255) String title,
                                       @NotBlank @Size(max = 4000) String description,
                                       @NotNull @DecimalMin("0.01") BigDecimal reservePrice,
                                       @NotNull @DecimalMin("0.01") BigDecimal minIncrement,
                                       @NotNull OffsetDateTime startTime,
                                       @NotNull OffsetDateTime endTime) {}

    public record PlaceBidRequest(@NotBlank String bidderId,
                                  @NotNull @DecimalMin("0.01") BigDecimal amount,
                                  @NotBlank String idempotencyKey) {}

    public record AuctionResponse(UUID id,
                                  String title,
                                  String description,
                                  BigDecimal reservePrice,
                                  BigDecimal minIncrement,
                                  OffsetDateTime startTime,
                                  OffsetDateTime endTime,
                                  String status,
                                  BigDecimal currentPrice,
                                  UUID winningBidId) {}

    public record BidResponse(UUID id,
                              UUID auctionId,
                              String bidderId,
                              BigDecimal amount,
                              Long sequenceNumber,
                              String idempotencyKey,
                              String status,
                              String rejectReason,
                              OffsetDateTime createdAt) {}

    public record AuctionResultResponse(UUID auctionId,
                                        String status,
                                        UUID winningBidId,
                                        String winnerBidderId,
                                        BigDecimal winningAmount,
                                        String message) {}

    public record PlaceBidResponse(String status, String rejectReason) {}

    private static AuctionResponse toAuctionResponse(AuctionJpaEntity auction) {
        return new AuctionResponse(
                auction.getId(),
                auction.getTitle(),
                auction.getDescription(),
                auction.getReservePrice(),
                auction.getMinIncrement(),
                auction.getStartTime(),
                auction.getEndTime(),
                auction.getStatus().name(),
                auction.getCurrentPrice(),
                auction.getWinningBidId());
    }

    private static BidResponse toBidResponse(BidJpaEntity bid) {
        return new BidResponse(
                bid.getId(),
                bid.getAuctionId(),
                bid.getBidderId(),
                bid.getAmount(),
                bid.getSequenceNumber(),
                bid.getIdempotencyKey(),
                bid.getBidStatus().name(),
                bid.getRejectReason(),
                bid.getCreatedAt());
    }

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

    private static Sort auctionSort(String sortBy, String sortDir) {
        String property = normalizeSortProperty(sortBy, Set.of("id", "title", "startTime", "endTime", "status", "currentPrice", "reservePrice"));
        Sort.Direction direction = parseSortDirection(sortDir);
        return Sort.by(direction, property);
    }

    private static Sort bidSort(String sortBy, String sortDir) {
        String property = normalizeSortProperty(sortBy, Set.of("id", "createdAt", "amount", "sequenceNumber", "bidderId", "bidStatus"));
        Sort.Direction direction = parseSortDirection(sortDir);
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

    private static Sort.Direction parseSortDirection(String sortDir) {
        return Sort.Direction.fromOptionalString(sortDir)
                .orElseThrow(() -> new IllegalArgumentException("sortDir must be ASC or DESC"));
    }

}
