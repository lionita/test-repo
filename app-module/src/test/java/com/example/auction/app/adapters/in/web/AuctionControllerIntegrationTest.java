package com.example.auction.app.adapters.in.web;

import com.example.auction.app.adapters.out.persistence.SpringDataAuctionRepository;
import com.example.auction.app.adapters.out.persistence.SpringDataBidRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @BeforeEach
    void setUp() {
        bidRepository.deleteAll();
        auctionRepository.deleteAll();
    }

    @Test
    void createAuctionThenStart_returnsExpectedStatuses() throws Exception {
        String createResponse = mockMvc.perform(post("/api/auctions")
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
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/auctions/")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(createResponse);
        UUID auctionId = UUID.fromString(body.get("auctionId").asText());

        mockMvc.perform(post("/api/auctions/{auctionId}/start", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());

        assertThat(auctionRepository.findById(auctionId)).isPresent();
        assertThat(auctionRepository.findById(auctionId).orElseThrow().getStatus().name()).isEqualTo("LIVE");
    }

    @Test
    void placeBid_afterAuctionStarted_acceptsBidAndPersistsIt() throws Exception {
        String createResponse = mockMvc.perform(post("/api/auctions")
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
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID auctionId = UUID.fromString(objectMapper.readTree(createResponse).get("auctionId").asText());

        mockMvc.perform(post("/api/auctions/{auctionId}/start", auctionId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("seller-1").claim("scope", "auction.write"))))
                .andExpect(status().isNoContent());

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
                .andExpect(status().isAccepted());

        assertThat(bidRepository.findAll())
                .hasSize(1)
                .first()
                .satisfies(bid -> {
                    assertThat(bid.getAuctionId()).isEqualTo(auctionId);
                    assertThat(bid.getBidderId()).isEqualTo("bidder-1");
                });
    }
}
