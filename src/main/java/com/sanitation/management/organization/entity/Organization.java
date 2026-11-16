package com.sanitation.management.organization.entity;

import com.sanitation.management.organization.enums.VerificationStatus;
import com.sanitation.management.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "organizations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_organizations_user_id", columnNames = "user_id"),
                @UniqueConstraint(name = "uk_organizations_registration_number", columnNames = "registration_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true)           // opt-in: lazy associations never touched
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    @ToString.Include
    private Long id;

    // Owning account. Unique FK => at most one organization per user.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_organizations_user"))
    private User user;

    @Column(name = "organization_name", nullable = false, length = 150)
    @ToString.Include
    private String organizationName;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "services_description", nullable = false, columnDefinition = "TEXT")
    private String servicesDescription;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 25)
    private String contactPhone;

    @Column(name = "business_address", nullable = false, length = 500)
    private String businessAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    @ToString.Include
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verification_notes", columnDefinition = "TEXT")
    private String verificationNotes;

    @Column(name = "accepting_requests", nullable = false)
    private Boolean acceptingRequests = Boolean.FALSE;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    // Many organizations can be verified by the same admin => ManyToOne, not OneToOne.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by",
            foreignKey = @ForeignKey(name = "fk_organizations_verified_by"))
    private User verifiedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.verificationStatus == null) {
            this.verificationStatus = VerificationStatus.PENDING;
        }
        if (this.acceptingRequests == null) {
            this.acceptingRequests = Boolean.FALSE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
