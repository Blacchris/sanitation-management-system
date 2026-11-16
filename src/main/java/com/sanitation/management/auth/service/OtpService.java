package com.sanitation.management.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generates OTPs and produces/checks the value stored on PendingRegistration.
 * Uses a keyed hash (HMAC) rather than a plain hash: a 6-digit code has only 1,000,000 possibilities,
 * so an unkeyed hash from a leaked database would be reversible in milliseconds.
 */
@Component
public class OtpService {

    private static final int OTP_BOUND = 1_000_000; // 6 digits

    private final SecretKeySpec hmacKey;
    private final Duration ttl;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public OtpService(@Value("${otp.hmac-key}") String base64Key,
                      @Value("${otp.ttl-minutes:10}") long ttlMinutes,
                      @Value("${otp.max-attempts:5}") int maxAttempts) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length < 32) {
            throw new IllegalStateException("otp.hmac-key must decode to at least 32 bytes");
        }
        this.hmacKey = new SecretKeySpec(raw, "HmacSHA256");
        this.ttl = Duration.ofMinutes(ttlMinutes);
        this.maxAttempts = maxAttempts;
    }

    public Duration ttl() {
        return ttl;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public String generate() {
        return String.format("%06d", random.nextInt(OTP_BOUND));
    }

    public String hash(String email, String otp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            byte[] digest = mac.doFinal((email + ":" + otp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to hash OTP", e);
        }
    }

    public boolean matches(String email, String otp, String expectedHash) {
        byte[] actual = hash(email, otp).getBytes(StandardCharsets.UTF_8);
        byte[] expected = expectedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expected);
    }
}
