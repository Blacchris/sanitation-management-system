package com.sanitation.management.auth.repository;

import com.sanitation.management.auth.entity.OtpEmailOutbox;
import com.sanitation.management.auth.entity.OutboxStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OtpEmailOutboxRepository extends JpaRepository<OtpEmailOutbox, Long> {

    // Fetches the registration in the same query: the worker has no open session for lazy loading.
    @EntityGraph(attributePaths = "pendingRegistration")
    List<OtpEmailOutbox> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxStatus status, Instant now);

    // The worker updates rows with targeted UPDATEs instead of save(): if the registration was verified
    // (and the row deleted) while an email was in flight, these affect 0 rows instead of re-inserting it.

    @Modifying
    @Transactional
    @Query("""
            update OtpEmailOutbox o
               set o.status = com.sanitation.management.auth.entity.OutboxStatus.SENT,
                   o.sentAt = :sentAt,
                   o.lastError = null,
                   o.otpCiphertext = null,
                   o.otpNonce = null
             where o.pendingRegistrationId = :id
            """)
    int markSent(@Param("id") Long id, @Param("sentAt") Instant sentAt);

    @Modifying
    @Transactional
    @Query("""
            update OtpEmailOutbox o
               set o.deliveryAttempts = :attempts,
                   o.nextAttemptAt = :nextAttemptAt,
                   o.lastError = :error
             where o.pendingRegistrationId = :id
            """)
    int markRetry(@Param("id") Long id,
                  @Param("attempts") int attempts,
                  @Param("nextAttemptAt") Instant nextAttemptAt,
                  @Param("error") String error);

    @Modifying
    @Transactional
    @Query("""
            update OtpEmailOutbox o
               set o.status = com.sanitation.management.auth.entity.OutboxStatus.FAILED,
                   o.deliveryAttempts = :attempts,
                   o.lastError = :error,
                   o.otpCiphertext = null,
                   o.otpNonce = null
             where o.pendingRegistrationId = :id
            """)
    int markFailed(@Param("id") Long id, @Param("attempts") int attempts, @Param("error") String error);

    @Override
    Optional<OtpEmailOutbox> findById(Long id);

    Optional<OtpEmailOutbox> findByPendingRegistrationId(Long id);
}
