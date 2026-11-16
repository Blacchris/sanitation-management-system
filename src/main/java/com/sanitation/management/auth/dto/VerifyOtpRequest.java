package com.sanitation.management.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyOtpRequest(
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "must be a 6-digit code")
        String otp
) {

    @Override
    public String toString() {
        return "VerifyOtpRequest{email=" + email + ", otp=******}";
    }
}
