package com.example.auction.app.adapters.in.web;

import com.example.auction.app.adapters.out.persistence.SpringDataAuctionRepository;
import com.example.auction.app.adapters.out.persistence.SpringDataBidRepository;
import com.example.auction.app.adapters.out.persistence.SpringDataBidderRepository;
import com.example.auction.app.adapters.out.persistence.SpringDataOutboxRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuctionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpringDataAuctionRepository auctionRepository;

    @Autowired
    private SpringDataBidRepository bidRepository;

    @Autowired
    private SpringDataBidderRepository bidderRepository;

    @Autowired
    private SpringDataOutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        outboxRepository.deleteAll();
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
        bidderRepository.deleteAll();
    }

    @Test
    void createAuctionThenStart_returnsExpectedStatuses() throws Exception {
        UUID auctionId = createAuction();
        assertThat(auctionRepository.findById(auctionId)).isPresent();
        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus().name()).isEqualTo("DRAFT");

        startAuction(auctionId);

        assertThat(auctionRepository.findById(auctionId)).isPresent();
        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus().name()).isEqualTo("LIVE");
    }

    @Test
    void createAuction_withTitleLongerThan255_returnsBadRequest() throws Exception {
        String title = "T".repeat(256);

        mockMvc.perform(post("/api/auctions")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Restored 1960s mechanical watch",
                                  "reservePrice": 100.00,
                                  "minIncrement": 5.00,
                                  "startTime": "2026-01-01T10:00:00Z",
                                  "endTime": "2026-01-01T12:00:00Z"
                                }
                                """.formatted(title)))
                 .andExpect(status().isBadRequest());

        assertThat(auctionRepository.findAll()).isEmpty();
    }

    @Test
    void createAuction_withDescriptionLongerThan4000_returnsBadRequest() throws Exception {
        String description = "D".repeat(4001);

        mockMvc.perform(post("/api/auctions")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Vintage Watch",
                                  "description": "%s",
                                  "reservePrice": 100.00,
                                  "minIncrement": 5.00,
                                  "startTime": "2026-01-01T10:00:00Z",
                                  "endTime": "2026-01-01T12:00:00Z"
                                }
                                """.formatted(description)))
                .andExpect(status().isBadRequest());

        assertThat(auctionRepository.findAll()).isEmpty();
    }

    @Test
    void createAuction_echoesCorrelationIdHeader() throws Exception {
        mockMvc.perform(post("/api/auctions")
                        .header("X-Correlation-Id", "corr-it-123")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Vintage Watch",
                                  "description": "Restored 1960s mechanical watch",
                                  "reservePrice": 100.00,
                                  "minIncrement": 5.00,
                                  "startTime": "2026-01-01T10:00:00Z",
                                  "endTime": "2026-01-01T12:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Correlation-Id", "corr-it-123"));
    }

    @Test
    void getAuctionById_returnsAuctionPayload() throws Exception {
        UUID auctionId = createAuction();

        String response = mockMvc.perform(get("/api/auctions/{id}", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("id").asText()).isEqualTo(auctionId.toString());
        assertThat(body.get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void updateAuction_updatesEditableFieldsWhenDraft() throws Exception {
        UUID auctionId = createAuction();

        String response = mockMvc.perform(put("/api/auctions/{id}", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated Vintage Watch",
                                  "description": "Updated description",
                                  "reservePrice": 150.00,
                                  "minIncrement": 15.00,
                                  "startTime": "2026-01-03T10:00:00Z",
                                  "endTime": "2026-01-03T12:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("id").asText()).isEqualTo(auctionId.toString());
        assertThat(body.get("title").asText()).isEqualTo("Updated Vintage Watch");
        assertThat(body.get("reservePrice").decimalValue()).isEqualByComparingTo("150.00");

        var persisted = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("Updated Vintage Watch");
        assertThat(persisted.getMinIncrement()).isEqualByComparingTo("15.00");
    }

    @Test
    void updateAuction_returnsConflictWhenAuctionIsLive() throws Exception {
        UUID auctionId = createAuction();
        startAuction(auctionId);

        mockMvc.perform(put("/api/auctions/{id}", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Updated Vintage Watch",
                                  "description": "Updated description",
                                  "reservePrice": 150.00,
                                  "minIncrement": 15.00,
                                  "startTime": "2026-01-03T10:00:00Z",
                                  "endTime": "2026-01-03T12:00:00Z"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void listAuctions_filtersByStatusAndQuery() throws Exception {
        UUID liveAuction = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(liveAuction);
        createAuction(
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(2));

        String response = mockMvc.perform(get("/api/auctions")
                        .param("status", "LIVE")
                        .param("query", "Vintage")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("page").asInt()).isEqualTo(0);
        assertThat(body.get("totalItems").asLong()).isEqualTo(1);
        assertThat(body.get("items")).hasSize(1);
        assertThat(body.get("items").get(0).get("id").asText()).isEqualTo(liveAuction.toString());
    }

    @Test
    void listAuctions_supportsPageAndSort() throws Exception {
        UUID zeta = createAuction("Zeta Camera", OffsetDateTime.now().plusDays(1), OffsetDateTime.now().plusDays(2));
        UUID alpha = createAuction("Alpha Camera", OffsetDateTime.now().plusDays(2), OffsetDateTime.now().plusDays(3));

        String firstPage = mockMvc.perform(get("/api/auctions")
                        .param("sortBy", "title")
                        .param("sortDir", "asc")
                        .param("page", "0")
                        .param("limit", "1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstPageBody = objectMapper.readTree(firstPage);
        assertThat(firstPageBody.get("page").asInt()).isEqualTo(0);
        assertThat(firstPageBody.get("totalItems").asLong()).isEqualTo(2);
        assertThat(firstPageBody.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPageBody.get("items")).hasSize(1);
        assertThat(firstPageBody.get("items").get(0).get("id").asText()).isEqualTo(alpha.toString());

        String secondPage = mockMvc.perform(get("/api/auctions")
                        .param("sortBy", "title")
                        .param("sortDir", "asc")
                        .param("page", "1")
                        .param("limit", "1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondPageBody = objectMapper.readTree(secondPage);
        assertThat(secondPageBody.get("page").asInt()).isEqualTo(1);
        assertThat(secondPageBody.get("totalItems").asLong()).isEqualTo(2);
        assertThat(secondPageBody.get("totalPages").asInt()).isEqualTo(2);
        assertThat(secondPageBody.get("items")).hasSize(1);
        assertThat(secondPageBody.get("items").get(0).get("id").asText()).isEqualTo(zeta.toString());
    }

    @Test
    void getAuctionBids_returnsLimitedList() throws Exception {
        UUID auctionId = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(auctionId);
        String bidderId = onboardBidder("1000.00");

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-get-1"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());

        String response = mockMvc.perform(get("/api/auctions/{id}/bids", auctionId)
                        .param("limit", "1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("page").asInt()).isEqualTo(0);
        assertThat(body.get("totalItems").asLong()).isEqualTo(1);
        assertThat(body.get("items")).hasSize(1);
        assertThat(body.get("items").get(0).get("status").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void getAuctionBids_supportsPageAndSort() throws Exception {
        UUID auctionId = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(auctionId);
        String bidderId = onboardBidder("1000.00");

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-page-sort-1"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 110.00,
                                  "idempotencyKey": "idem-page-sort-2"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());

        String firstPage = mockMvc.perform(get("/api/auctions/{id}/bids", auctionId)
                        .param("sortBy", "amount")
                        .param("sortDir", "asc")
                        .param("page", "0")
                        .param("limit", "1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode firstPageBody = objectMapper.readTree(firstPage);
        assertThat(firstPageBody.get("page").asInt()).isEqualTo(0);
        assertThat(firstPageBody.get("totalItems").asLong()).isEqualTo(2);
        assertThat(firstPageBody.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPageBody.get("items")).hasSize(1);
        assertThat(firstPageBody.get("items").get(0).get("amount").decimalValue()).isEqualByComparingTo("105.00");

        String secondPage = mockMvc.perform(get("/api/auctions/{id}/bids", auctionId)
                        .param("sortBy", "amount")
                        .param("sortDir", "asc")
                        .param("page", "1")
                        .param("limit", "1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondPageBody = objectMapper.readTree(secondPage);
        assertThat(secondPageBody.get("page").asInt()).isEqualTo(1);
        assertThat(secondPageBody.get("totalItems").asLong()).isEqualTo(2);
        assertThat(secondPageBody.get("totalPages").asInt()).isEqualTo(2);
        assertThat(secondPageBody.get("items")).hasSize(1);
        assertThat(secondPageBody.get("items").get(0).get("amount").decimalValue()).isEqualByComparingTo("110.00");
    }

    @Test
    void getAuctionResult_returnsWinnerAfterClose() throws Exception {
        UUID auctionId = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(auctionId);
        String bidderId = onboardBidder("1000.00");

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-result-1"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());

        var auction = auctionRepository.findById(auctionId).orElseThrow();
        auction.setEndTime(OffsetDateTime.now().minusSeconds(1));
        auctionRepository.save(auction);

        mockMvc.perform(post("/api/auctions/{auctionId}/close", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());

        String response = mockMvc.perform(get("/api/auctions/{id}/result", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "auction.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("status").asText()).isEqualTo("CLOSED");
        assertThat(body.get("winningBidId").isNull()).isFalse();
        assertThat(body.get("winnerBidderId").asText()).isEqualTo(bidderId);
    }

    @Test
    void onboardBidder_thenPlaceBid_afterAuctionStarted_acceptsBidAndPersistsIt() throws Exception {
        UUID auctionId = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(auctionId);
        String bidderId = onboardBidder("500.00");

        assertThat(bidderRepository.findById(bidderId)).isPresent();
        assertThat(bidderRepository.findById(bidderId).orElseThrow().getEmail()).isEqualTo("generated@example.com");

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-1"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());

        assertThat(bidRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(bid -> {
                    assertThat(bid.getAuctionId()).isEqualTo(auctionId);
                    assertThat(bid.getBidderId()).isEqualTo(bidderId);
                });
    }

    @Test
    void placeBid_withoutOnboarding_returnsConflict() throws Exception {
        UUID auctionId = createAuction();
        startAuction(auctionId);

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("bidder-1").claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "bidder-1",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-1"
                                }
                                """))
                .andExpect(status().isConflict());

        assertThat(bidRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(bid -> {
                    assertThat(bid.getBidStatus().name()).isEqualTo("REJECTED");
                    assertThat(bid.getRejectReason()).isNotBlank();
                });
    }

    @Test
    void placeBid_withBidderDifferentFromJwtSubject_returnsBadRequest() throws Exception {
        UUID auctionId = createAuction();
        startAuction(auctionId);

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                 .jwt(jwt -> jwt.subject("actual-bidder").claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "spoofed-bidder",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-mismatch"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(bidRepository.findAll()).isEmpty();
    }

    @Test
    void onboardBidder_withBidWriteScopeOnly_returnsForbidden() throws Exception {
        mockMvc.perform(post("/api/bidders/onboarding")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                 .jwt(jwt -> jwt.subject("actual-bidder").claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Spoofed",
                                  "lastName": "Bidder",
                                  "email": "spoofed@example.com",
                                  "nationalId": "NID-SPOOF-1",
                                  "purchasingAuthorizationLimit": 500.00
                                }
                                """))
                .andExpect(status().isForbidden());

        assertThat(bidderRepository.findAll()).isEmpty();
    }

    @Test
    void listBidders_supportsPaginationAndSorting_andExcludesSoftDeleted() throws Exception {
        String alphaId = onboardBidder("Alice", "Alpha", "alice@example.com", "NID-ALICE", "100.00");
        String zetaId = onboardBidder("Zara", "Zulu", "zara@example.com", "NID-ZARA", "100.00");

        mockMvc.perform(delete("/api/bidders/{bidderId}", zetaId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write"))))
                .andExpect(status().isNoContent());

        String response = mockMvc.perform(get("/api/bidders")
                        .param("sortBy", "firstName")
                        .param("sortDir", "asc")
                        .param("page", "0")
                        .param("limit", "10")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("page").asInt()).isEqualTo(0);
        assertThat(body.get("totalItems").asLong()).isEqualTo(1);
        assertThat(body.get("items")).hasSize(1);
        assertThat(body.get("items").get(0).get("bidderId").asText()).isEqualTo(alphaId);

        String emptySecondPage = mockMvc.perform(get("/api/bidders")
                        .param("sortBy", "firstName")
                        .param("sortDir", "asc")
                        .param("page", "1")
                        .param("limit", "1")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write"))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode secondPageBody = objectMapper.readTree(emptySecondPage);
        assertThat(secondPageBody.get("page").asInt()).isEqualTo(1);
        assertThat(secondPageBody.get("totalItems").asLong()).isEqualTo(1);
        assertThat(secondPageBody.get("items")).isEmpty();
    }


    @Test
    void updateBidder_updatesPersistedFields() throws Exception {
        String bidderId = onboardBidder("500.00");

        String updateResponse = mockMvc.perform(put("/api/bidders/{bidderId}", bidderId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Janet",
                                  "lastName": "Smith",
                                  "email": "janet.smith@example.com",
                                  "nationalId": "NID-UPDATED",
                                  "purchasingAuthorizationLimit": 900.00
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(updateResponse);
        assertThat(body.get("bidderId").asText()).isEqualTo(bidderId);
        assertThat(body.get("firstName").asText()).isEqualTo("Janet");

        var bidder = bidderRepository.findById(bidderId).orElseThrow();
        assertThat(bidder.getFirstName()).isEqualTo("Janet");
        assertThat(bidder.getEmail()).isEqualTo("janet.smith@example.com");
    }

    @Test
    void blockBidder_preventsBidding_untilUnblocked() throws Exception {
        UUID auctionId = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(auctionId);
        String bidderId = onboardBidder("500.00");

        mockMvc.perform(post("/api/bidders/{bidderId}/block", bidderId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write"))))
                .andExpect(status().isNoContent());

        assertThat(bidderRepository.findById(bidderId)).isPresent();
        assertThat(bidderRepository.findById(bidderId).orElseThrow().getBlockedUntil()).isNotNull();

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-blocked"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/bidders/{bidderId}/unblock", bidderId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write"))))
                .andExpect(status().isNoContent());

        assertThat(bidderRepository.findById(bidderId)).isPresent();
        assertThat(bidderRepository.findById(bidderId).orElseThrow().getBlockedUntil()).isNull();

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-unblocked"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());
    }

    @Test
    void softDeleteBidder_preventsFutureBidAuthorization() throws Exception {
        UUID auctionId = createAuction();
        startAuction(auctionId);
        String bidderId = onboardBidder("500.00");

        mockMvc.perform(delete("/api/bidders/{bidderId}", bidderId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write"))))
                .andExpect(status().isNoContent());

        assertThat(bidderRepository.findById(bidderId)).isPresent();
        assertThat(bidderRepository.findById(bidderId).orElseThrow().getDeletedAt()).isNotNull();

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-deleted"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isConflict());
    }

    @Test
    void settleAuction_transitionsToSettledAndWritesSettledEvent() throws Exception {
        UUID auctionId = createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
        startAuction(auctionId);
        String bidderId = onboardBidder("500.00");

        mockMvc.perform(post("/api/auctions/{auctionId}/bids", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject(bidderId).claim("scope", "bid.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bidderId": "%s",
                                  "amount": 105.00,
                                  "idempotencyKey": "idem-1"
                                }
                                """.formatted(bidderId)))
                .andExpect(status().isAccepted());

        var auction = auctionRepository.findById(auctionId).orElseThrow();
        auction.setEndTime(OffsetDateTime.now().minusSeconds(1));
        auctionRepository.save(auction);

        mockMvc.perform(post("/api/auctions/{auctionId}/close", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auctions/{auctionId}/settle", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());

        var settledAuction = auctionRepository.findById(auctionId).orElseThrow();
        assertThat(settledAuction.getStatus().name()).isEqualTo("SETTLED");
        assertThat(settledAuction.getWinningBidId()).isNotNull();
        assertThat(outboxRepository.findAll())
                .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("auction.closed"));
        assertThat(outboxRepository.findAll())
                .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("auction.settled"));
    }

    @Test
    void cancelAuction_transitionsToCancelled() throws Exception {
        UUID auctionId = createAuction();

        mockMvc.perform(post("/api/auctions/{auctionId}/cancel", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());

        assertThat(auctionRepository.findById(auctionId)).isPresent();
        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus().name()).isEqualTo("CANCELLED");
        assertThat(outboxRepository.findAll())
                .anySatisfy(event -> assertThat(event.getEventType()).isEqualTo("auction.cancelled"));
    }

    private UUID createAuction() throws Exception {
        return createAuction(
                OffsetDateTime.now().minusMinutes(1),
                OffsetDateTime.now().plusHours(1));
    }

    private UUID createAuction(OffsetDateTime startTime, OffsetDateTime endTime) throws Exception {
        return createAuction("Vintage Watch", startTime, endTime);
    }

    private UUID createAuction(String title, OffsetDateTime startTime, OffsetDateTime endTime) throws Exception {
        String createResponse = mockMvc.perform(post("/api/auctions")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "Restored 1960s mechanical watch",
                                  "reservePrice": 100.00,
                                  "minIncrement": 5.00,
                                  "startTime": "%s",
                                  "endTime": "%s"
                                }
                                """.formatted(title, startTime, endTime)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/auctions/")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(createResponse);
        return UUID.fromString(body.get("auctionId").asText());
    }

    private void startAuction(UUID auctionId) throws Exception {
        scheduleAuction(auctionId);
        mockMvc.perform(post("/api/auctions/{auctionId}/start", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());
    }

    private void scheduleAuction(UUID auctionId) throws Exception {
        mockMvc.perform(post("/api/auctions/{auctionId}/schedule", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());
    }

    private String onboardBidder(String limit) throws Exception {
        return onboardBidder("Jane", "Doe", "generated@example.com", "NID-GENERATED", limit);
    }

    private String onboardBidder(String firstName,
                                 String lastName,
                                 String email,
                                 String nationalId,
                                 String limit) throws Exception {
        String onboardingResponse = mockMvc.perform(post("/api/bidders/onboarding")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("admin-1").claim("scope", "admin.write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "%s",
                                  "lastName": "%s",
                                  "email": "%s",
                                  "nationalId": "%s",
                                  "purchasingAuthorizationLimit": %s
                                }
                                """.formatted(firstName, lastName, email, nationalId, limit)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(onboardingResponse);
        assertThat(body.get("bidderId").asText()).isNotBlank();
        assertThat(body.get("firstName").asText()).isEqualTo(firstName);
        assertThat(body.get("lastName").asText()).isEqualTo(lastName);
        assertThat(body.get("email").asText()).isEqualTo(email);
        assertThat(body.get("nationalId").asText()).isEqualTo(nationalId);
        assertThat(body.get("blockedUntil").isNull()).isTrue();
        return body.get("bidderId").asText();
    }
}
