package com.sanitation.management.auth.dto;

import com.sanitation.management.auth.entity.DeliveryChannel;

public record RegistrationResponse(
        String message,
        String email,              // still the key for POST /auth/verify
        DeliveryChannel channel,
        String sentTo,             // masked destination
        long otpExpiresInSeconds) {
}
