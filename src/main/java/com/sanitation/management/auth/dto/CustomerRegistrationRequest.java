package com.sanitation.management.auth.dto;

import com.sanitation.management.auth.entity.DeliveryChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRegistrationRequest(
        @NotBlank
        @Size(max = 120)
        String fullName,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @NotBlank
        @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE)
        String phone,

        @NotBlank
        @Pattern(regexp = ValidationRules.PASSWORD_REGEX, message = ValidationRules.PASSWORD_MESSAGE)
        String password,

        @NotNull
        DeliveryChannel otpChannel
) {


    // Records auto-generate a toString that prints every component; mask the password.
    @Override
    public String toString() {
        return "CustomerRegistrationRequest{email=" + email + ", password=****}";
    }
}
