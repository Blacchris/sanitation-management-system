package com.sanitation.management.security;

import com.sanitation.management.organization.enums.VerificationStatus;
import com.sanitation.management.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Organizations are standalone (no user account), so access is checked by organization id.
 * Status is read from the database on every check, since an organization's status can change at any time.
 *
 * Usage: {@code @PreAuthorize("@organizationAccess.isVerified(#organizationId)")}
 */
@Component("organizationAccess")
@RequiredArgsConstructor
public class OrganizationAccess {

    private final OrganizationRepository organizationRepository;

    public boolean isVerified(Long organizationId) {
        return organizationId != null
                && organizationRepository.existsByIdAndVerificationStatus(organizationId, VerificationStatus.VERIFIED);
    }
}
