package com.sanitation.management.organization.repository;

import com.sanitation.management.organization.entity.Organization;
import com.sanitation.management.organization.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    boolean existsByIdAndVerificationStatus(Long id, VerificationStatus status);

    boolean existsByRegistrationNumber(String registrationNumber);

    boolean existsByContactEmail(String contactEmail);

    boolean existsByContactPhone(String contactPhone);
}
