package com.sanitation.management.organization.repository;

import com.sanitation.management.organization.entity.Organization;
import com.sanitation.management.organization.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndVerificationStatus(Long userId, VerificationStatus status);

    boolean existsByRegistrationNumber(String registrationNumber);
}
