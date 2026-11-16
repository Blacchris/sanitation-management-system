package com.sanitation.management.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sanitation.management.organization.entity.Organization;
import com.sanitation.management.security.AccessToken;
import com.sanitation.management.user.entity.User;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        UserSummary user,
        OrganizationSummary organization) {

    /** @param organization null for customers */
    public static AuthResponse of(AccessToken token, User user, Organization organization) {
        return new AuthResponse(
                "Bearer",
                token.value(),
                token.expiresInSeconds(),
                UserSummary.from(user),
                organization == null ? null : OrganizationSummary.from(organization));
    }
}
