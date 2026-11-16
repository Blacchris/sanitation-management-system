package com.sanitation.management.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret          Base64-encoded HMAC key (>= 32 bytes decoded), supplied via the JWT_SECRET env var
 * @param issuer          value of the "iss" claim; tokens from other issuers are rejected
 * @param accessTokenTtl  lifetime of an access token
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {

    // Never let the key leak through logging or actuator dumps.
    @Override
    public String toString() {
        return "JwtProperties{secret=****, issuer=" + issuer + ", accessTokenTtl=" + accessTokenTtl + "}";
    }
}
