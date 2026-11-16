package com.sanitation.management.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No strength rules here: those apply when a password is created, and old passwords must still work. */
public record LoginRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {

    @Override
    public String toString() {
        return "LoginRequest{email=" + email + ", password=****}";
    }
}