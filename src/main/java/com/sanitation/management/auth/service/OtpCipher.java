package com.sanitation.management.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts the one-time code while it waits in the outbox (AES-256-GCM).
 * Only the OTP itself is encrypted: no extra "associated data" is used.
 */
@Component
public class OtpCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public OtpCipher(
            @Value("${otp.enc-key}") String base64Key) {

        byte[] raw = Base64.getDecoder().decode(base64Key);

        if (raw.length != 32) {
            throw new IllegalStateException(
                    "otp.enc-key must be 32 bytes"
            );
        }

        this.key = new SecretKeySpec(raw, "AES");
        Arrays.fill(raw, (byte) 0);
    }

    public EncryptedOtp encrypt(String plaintext) throws GeneralSecurityException {

        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, nonce)
        );

        byte[] ciphertext = cipher.doFinal(
                plaintext.getBytes(StandardCharsets.UTF_8)
        );

        return new EncryptedOtp(ciphertext, nonce);
    }

    public String decrypt(
            byte[] ciphertext,
            byte[] nonce) throws GeneralSecurityException {

        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new IllegalArgumentException(
                    "Invalid AES-GCM nonce"
            );
        }

        if (ciphertext == null || ciphertext.length < 16) {
            throw new IllegalArgumentException(
                    "Invalid AES-GCM ciphertext"
            );
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

        cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                new GCMParameterSpec(TAG_BITS, nonce)
        );

        byte[] plaintext = cipher.doFinal(ciphertext);

        return new String(plaintext, StandardCharsets.UTF_8);
    }

    public record EncryptedOtp(
            byte[] ciphertext,
            byte[] nonce
    ) {}
}