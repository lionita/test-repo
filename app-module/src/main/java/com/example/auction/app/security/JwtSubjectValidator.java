package com.example.auction.app.security;

import org.springframework.security.oauth2.jwt.Jwt;

public final class JwtSubjectValidator {
    private JwtSubjectValidator() {
    }

    public static void requireSubject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new IllegalArgumentException("jwt subject (sub) is required");
        }
    }
}
