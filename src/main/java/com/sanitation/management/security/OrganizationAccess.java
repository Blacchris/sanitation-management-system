package com.sanitation.management.security;

import com.sanitation.management.organization.enums.VerificationStatus;
import com.sanitation.management.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Verification state is read from the database on every check, never from the token:
 * a JWT is only proof of identity at issue time, and an organization's status can change afterwards.
 *
 * Usage on any organization-only operation:
 * {@code @PreAuthorize("hasRole('ORGANIZATION') and @organizationAccess.isVerified(authentication)")}
 */
@Component("organizationAccess")
@RequiredArgsConstructor
public class OrganizationAccess {

    private final OrganizationRepository organizationRepository;

    public boolean isVerified(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return false;
        }
        try {
            Long userId = Long.valueOf(jwtAuth.getToken().getSubject());
            return organizationRepository.existsByUserIdAndVerificationStatus(userId, VerificationStatus.VERIFIED);
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
