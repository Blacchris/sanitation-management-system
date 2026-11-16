package com.sanitation.management.auth.service;

import com.sanitation.management.auth.entity.OtpEmailOutbox;
import com.sanitation.management.notification.BrevoClient;
import com.sanitation.management.auth.entity.OutboxStatus;
import com.sanitation.management.auth.repository.OtpEmailOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Delivery is at-least-once: if the email is accepted but marking the row SENT fails, the next poll
 * sends it again. Assumes a single application instance (see notes on multi-instance deployments).
 *
 * Delivery goes through BrevoClient: email or SMS, depending on the channel stored on the outbox row.
 */
@Component
@Slf4j
public class OtpEmailOutboxWorker {

    private static final String SUBJECT = "Verify your new account";
    private static final int MAX_ERROR_LENGTH = 500;

    private final OtpEmailOutboxRepository otpEmailOutboxRepo;
    private final OtpCipher otpCipher;
    private final BrevoClient brevo;
    private final int maxDeliveryAttempts;


    public OtpEmailOutboxWorker(OtpEmailOutboxRepository otpEmailOutboxRepo,
                                OtpCipher otpCipher,
                                BrevoClient brevo,
                                @Value("${otp.outbox.max-delivery-attempts:5}") int maxDeliveryAttempts) {
        this.otpEmailOutboxRepo = otpEmailOutboxRepo;
        this.otpCipher = otpCipher;
        this.brevo = brevo;
        this.maxDeliveryAttempts = maxDeliveryAttempts;
    }

    @Scheduled(fixedDelayString = "${otp.outbox.poll-delay-ms:5000}")
    public void pollOutbox() {
        List<OtpEmailOutbox> dueRows = otpEmailOutboxRepo
                .findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        OutboxStatus.PENDING, Instant.now());

        for (OtpEmailOutbox outbox : dueRows) {
            process(outbox);
        }
    }

    private void process(OtpEmailOutbox outbox) {
        Long id = outbox.getPendingRegistrationId();
        try {
            if (outbox.getPendingRegistration().getOtpExpiresAt().isBefore(Instant.now())) {
                otpEmailOutboxRepo.markFailed(id, outbox.getDeliveryAttempts(), "OTP expired before delivery");
                log.warn("OTP expired before delivery for registration ID {}", id);
                return;
            }

            String otpText;
            try {
                otpText = otpCipher.decrypt(
                        outbox.getOtpCiphertext(),
                        outbox.getOtpNonce());
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                // Permanent: retrying cannot make tampered or malformed data valid.
                log.error("OTP decryption failed for registration ID {}", id, e);
                otpEmailOutboxRepo.markFailed(id, outbox.getDeliveryAttempts(), "OTP decryption failed");
                return;
            }

            try {
                switch (outbox.getChannel()) {
                    case EMAIL -> brevo.sendEmail(outbox.getRecipient(), SUBJECT, buildBody(otpText));
                    case SMS -> brevo.sendSms(outbox.getRecipient(), buildSmsBody(otpText));
                }
            } catch (Exception e) {
                onDeliveryFailure(outbox, e);
                return;
            }

            otpEmailOutboxRepo.markSent(id, Instant.now());
            log.info("OTP email processed for outbox registration ID {}", id);

        } catch (Exception e) {
            // Never let one bad row stop the rest of the batch.
            log.error("OTP email processing failed for registration ID {}", id, e);
        }
    }

    private void onDeliveryFailure(OtpEmailOutbox outbox, Exception e) {
        Long id = outbox.getPendingRegistrationId();
        int attempts = outbox.getDeliveryAttempts() + 1;
        String error = abbreviate(e.getClass().getSimpleName() + ": " + e.getMessage());

        if (attempts >= maxDeliveryAttempts) {
            log.error("OTP email permanently failed for registration ID {} after {} attempts", id, attempts, e);
            otpEmailOutboxRepo.markFailed(id, attempts, error);
        } else {
            Instant next = Instant.now().plus(backoff(attempts));
            log.warn("OTP email failed for registration ID {} (attempt {}), retrying at {}", id, attempts, next, e);
            otpEmailOutboxRepo.markRetry(id, attempts, next, error);
        }
    }

    private static Duration backoff(int attempts) {
        return Duration.ofSeconds(15L * attempts * attempts);
    }

    private static String buildBody(String otpText) {
        return String.format("Verify your new account\n"
                + "To verify your email address, please use the following One Time Password (OTP):\n %s\n"
                + "Don't share this OTP with anyone", otpText);
    }

    private static String buildSmsBody(String otpText) {
        return "Your verification code is " + otpText + ". Don't share it with anyone.";
    }

    private static String abbreviate(String text) {
        return text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }
}
