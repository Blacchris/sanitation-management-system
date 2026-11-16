package com.sanitation.management.auth.dto;

import com.sanitation.management.auth.entity.DeliveryChannel;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OrganizationRegistrationRequest(
        // account
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

        // organization
        @NotBlank @Size(max = 150) String organizationName,
        @Size(max = 100) String registrationNumber,
        @Size(max = 5000) String description,
        @NotBlank @Size(max = 5000) String servicesDescription,
        @Email @Size(max = 254) String contactEmail,
        @NotBlank @Pattern(regexp = ValidationRules.PHONE_REGEX, message = ValidationRules.PHONE_MESSAGE) String contactPhone,
        @NotBlank @Size(max = 500) String businessAddress,
        // how the OTP is delivered
        @NotNull DeliveryChannel otpChannel) {

    @Override
    public String toString() {
        return "OrganizationRegistrationRequest{email=" + email + ", organizationName=" + organizationName
                + ", password=****}";
    }
}
