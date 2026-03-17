package com.example.auction.app.adapters.in.web.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RealtimePushControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void subscribe_requiresAuthenticatedJwt() throws Exception {
        mockMvc.perform(get("/api/realtime/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_withJwt_returnsOk() throws Exception {
        mockMvc.perform(get("/api/realtime/events")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("viewer-1").claim("scope", "bid.write"))))
                .andExpect(status().isOk());
    }
}
