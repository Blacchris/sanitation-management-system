package com.sanitation.management.auth.repository;

import com.sanitation.management.auth.entity.PendingRegistration;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    /**
     * SELECT ... FOR UPDATE. Serializes concurrent verify/re-register calls for the same email, so
     * parallel guesses cannot all read "attempts = 0". Must run inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PendingRegistration p where p.email = :email")
    Optional<PendingRegistration> findByEmailForUpdate(@Param("email") String email);
}
