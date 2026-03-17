package com.example.auction.app.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSubjectValidatorTest {
    @Test
    void rejectsMissingSubject() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("scope", "bid.write"));
        assertThrows(IllegalArgumentException.class, () -> JwtSubjectValidator.requireSubject(jwt));
    }

    @Test
    void acceptsSubjectPresent() {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), Map.of("sub", "user-1", "scope", "auction.write"));
        assertDoesNotThrow(() -> JwtSubjectValidator.requireSubject(jwt));
    }
}
