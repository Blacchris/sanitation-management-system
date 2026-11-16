package com.sanitation.management.auth.dto;

import com.sanitation.management.organization.entity.Organization;
import com.sanitation.management.organization.enums.VerificationStatus;

public record OrganizationSummary(
        Long id,
        String organizationName,
        String registrationNumber,
        String contactEmail,
        String contactPhone,
        String businessAddress,
        VerificationStatus verificationStatus,
        Boolean acceptingRequests) {

    public static OrganizationSummary from(Organization organization) {
        return new OrganizationSummary(organization.getId(), organization.getOrganizationName(),
                organization.getRegistrationNumber(), organization.getContactEmail(),
                organization.getContactPhone(), organization.getBusinessAddress(),
                organization.getVerificationStatus(), organization.getAcceptingRequests());
    }
}
