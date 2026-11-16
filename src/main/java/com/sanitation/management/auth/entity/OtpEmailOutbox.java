package com.sanitation.management.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;


@Entity
@Table(
        name = "otp_email_outbox",
        indexes = @Index(name = "idx_otp_outbox_status_next_attempt", columnList = "status,next_attempt_at")
)
@Getter
@Setter
@NoArgsConstructor
public class OtpEmailOutbox {

    @Id
    @Column(name = "pending_registration_id")
    private Long pendingRegistrationId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pending_registration_id", nullable = false)
    private PendingRegistration pendingRegistration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeliveryChannel channel = DeliveryChannel.EMAIL;

    @Column(name = "recipient", nullable = false, length = 254)
    private String recipient;

    @Column(name = "otp_ciphertext", length = 64)
    private byte[] otpCiphertext;

    @Column(name = "otp_nonce", length = 16)
    private byte[] otpNonce;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "delivery_attempts", nullable = false)
    private int deliveryAttempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void resetDeliveryState() {
        this.status = OutboxStatus.PENDING;
        this.deliveryAttempts = 0;
        this.nextAttemptAt = Instant.now();
        this.lastError = null;
        this.sentAt = null;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
    }
}
