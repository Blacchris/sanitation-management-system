package com.sanitation.management.auth.dto;

import com.sanitation.management.security.AccessToken;
import com.sanitation.management.user.entity.User;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UserSummary user) {

    public static AuthResponse of(AccessToken token, User user) {
        return new AuthResponse("Bearer", token.value(), token.expiresInSeconds(), UserSummary.from(user));
    }
}
