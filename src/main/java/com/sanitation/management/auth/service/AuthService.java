package com.sanitation.management.auth.service;

import com.sanitation.management.auth.dto.AuthResponse;
import com.sanitation.management.auth.dto.CustomerRegistrationRequest;
import com.sanitation.management.auth.dto.LoginRequest;
import com.sanitation.management.auth.dto.OrganizationRegistrationRequest;
import com.sanitation.management.auth.dto.RegistrationResponse;
import com.sanitation.management.auth.dto.VerifyOtpRequest;
import com.sanitation.management.auth.entity.DeliveryChannel;
import com.sanitation.management.auth.entity.OtpEmailOutbox;
import com.sanitation.management.auth.entity.PendingRegistration;
import com.sanitation.management.auth.repository.OtpEmailOutboxRepository;
import com.sanitation.management.auth.repository.PendingRegistrationRepository;
import com.sanitation.management.common.exception.*;
import com.sanitation.management.organization.entity.Organization;
import com.sanitation.management.organization.enums.VerificationStatus;
import com.sanitation.management.organization.repository.OrganizationRepository;
import com.sanitation.management.security.JwtService;
import com.sanitation.management.user.entity.User;
import com.sanitation.management.user.enums.AccountStatus;
import com.sanitation.management.user.enums.Role;
import com.sanitation.management.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.security.auth.login.AccountLockedException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final String REGISTRATION_MESSAGE =
            "A verification code will be sent shortly";

    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final OtpEmailOutboxRepository otpEmailOutboxRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpCipher otpCipher;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final int maxFailedLoginAttempts;
    private final Duration loginLockDuration;

    /** A real BCrypt hash compared against when the email is unknown, so both login paths cost the same time. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       PendingRegistrationRepository pendingRegistrationRepository,
                       OtpEmailOutboxRepository otpEmailOutboxRepository,
                       PasswordEncoder passwordEncoder,
                       OtpCipher otpCipher,
                       OtpService otpService,
                       JwtService jwtService,
                       @Value("${auth.login.max-failed-attempts:5}") int maxFailedLoginAttempts,
                       @Value("${auth.login.lock-minutes:15}") long lockMinutes) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.pendingRegistrationRepository = pendingRegistrationRepository;
        this.otpEmailOutboxRepository = otpEmailOutboxRepository;
        this.passwordEncoder = passwordEncoder;
        this.otpCipher = otpCipher;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
        this.loginLockDuration = Duration.ofMinutes(lockMinutes);
        this.dummyHash = passwordEncoder.encode("timing-equalisation-" + UUID.randomUUID());
    }



    @Transactional
    public RegistrationResponse registerCustomer(CustomerRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        String phone = request.phone().trim();
        DeliveryChannel channel = resolveChannel(request.otpChannel());
        assertAccountAvailable(email, phone);

        PendingRegistration pending = findOrCreatePending(email);
        assertResendAllowed(pending);


        fillAccount(pending, request.fullName(), email, phone, request.password(), Role.CUSTOMER, channel);
        clearOrganizationDetails(pending);

        return issueOtp(pending, channel);
    }

    @Transactional
    public RegistrationResponse registerOrganization(OrganizationRegistrationRequest request) {
        String email = normalizeEmail(request.email());
        String phone = request.phone().trim();
        String registrationNumber = blankToNull(request.registrationNumber());
        DeliveryChannel channel = resolveChannel(request.otpChannel());

        assertAccountAvailable(email, phone);
        assertRegistrationNumberAvailable(registrationNumber);

        PendingRegistration pending = findOrCreatePending(email);
        assertResendAllowed(pending);

        fillAccount(pending, request.fullName(), email, phone, request.password(), Role.ORGANIZATION, channel);
        pending.setOrganizationName(request.organizationName().trim());
        pending.setRegistrationNumber(registrationNumber);
        pending.setDescription(blankToNull(request.description()));
        pending.setServicesDescription(request.servicesDescription().trim());
        String contactEmail = blankToNull(request.contactEmail());
        pending.setContactEmail(contactEmail == null ? null : normalizeEmail(contactEmail));
        pending.setContactPhone(request.contactPhone().trim());
        pending.setBusinessAddress(request.businessAddress().trim());

        return issueOtp(pending, channel);
    }

    private RegistrationResponse issueOtp(PendingRegistration pendingRegistration, DeliveryChannel channel) {
        String otpText = otpService.generate();
        pendingRegistration.setOtpHash(otpService.hash(pendingRegistration.getEmail(), otpText));
        pendingRegistration.setOtpExpiresAt(Instant.now().plus(otpService.ttl()));
        pendingRegistration.setOtpAttempts(0);


        pendingRegistrationRepository.saveAndFlush(pendingRegistration);

        Long registrationId = pendingRegistration.getId();

        if (registrationId == null) {
            throw new IllegalStateException(
                    "Pending registration must have an ID before the outbox row can be created"
            );
        }

        OtpCipher.EncryptedOtp encrypted;

        try {
            encrypted = otpCipher.encrypt(otpText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt OTP", e);
        }

        //Explain why the OTP
        OtpEmailOutbox outbox = otpEmailOutboxRepository.findById(registrationId)
                .orElseGet(OtpEmailOutbox::new);

        outbox.setPendingRegistration(pendingRegistration);
        String destination = channel == DeliveryChannel.SMS
                ? pendingRegistration.getPhone()
                : pendingRegistration.getEmail();
        outbox.setChannel(channel);
        outbox.setRecipient(destination);
        outbox.setOtpCiphertext(encrypted.ciphertext());
        outbox.setOtpNonce(encrypted.nonce());
        outbox.resetDeliveryState();


        otpEmailOutboxRepository.save(outbox);

        return new RegistrationResponse(REGISTRATION_MESSAGE, pendingRegistration.getEmail(), channel,
                mask(destination, channel), otpService.ttl().toSeconds());
    }


    @Transactional(noRollbackFor = {InvalidOtpException.class, TooManyAttemptsException.class})
    public AuthResponse verifyRegistration(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());

        PendingRegistration pending = pendingRegistrationRepository.findByEmailForUpdate(email)
                .orElseThrow(InvalidOtpException::new);

        if (pending.getOtpAttempts() >= otpService.maxAttempts()) {
            throw new TooManyAttemptsException();
        }
        if (Instant.now().isAfter(pending.getOtpExpiresAt())) {
            throw new InvalidOtpException();
        }
        if (!otpService.matches(email, request.otp(), pending.getOtpHash())) {
            int attempts = pending.getOtpAttempts() + 1;
            pending.setOtpAttempts(attempts);
            pendingRegistrationRepository.saveAndFlush(pending);
            throw attempts >= otpService.maxAttempts() ? new TooManyAttemptsException() : new InvalidOtpException();
        }

        assertAccountAvailable(email, pending.getPhone());
        if (pending.getRole() == Role.ORGANIZATION) {
            assertRegistrationNumberAvailable(pending.getRegistrationNumber());
        }

        User user = new User();
        user.setFullName(pending.getFullName());
        user.setEmail(pending.getEmail());
        user.setPhone(pending.getPhone());
        user.setPasswordHash(pending.getPasswordHash());
        user.setRole(pending.getRole());
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        Organization organization = null;
        if (pending.getRole() == Role.ORGANIZATION) {
            organization = new Organization();
            organization.setUser(user);
            organization.setOrganizationName(pending.getOrganizationName());
            organization.setRegistrationNumber(pending.getRegistrationNumber());
            organization.setDescription(pending.getDescription());
            organization.setServicesDescription(pending.getServicesDescription());
            organization.setContactEmail(pending.getContactEmail());
            organization.setContactPhone(pending.getContactPhone());
            organization.setBusinessAddress(pending.getBusinessAddress());
            organization.setVerificationStatus(VerificationStatus.PENDING); // OTP != admin verification
            organization.setAcceptingRequests(Boolean.FALSE);
            organizationRepository.saveAndFlush(organization);
        }

        // Child (outbox) first, then parent (pending): the outbox PK is a foreign key to the pending row.
        otpEmailOutboxRepository.deleteById(pending.getId());
        pendingRegistrationRepository.delete(pending);

        return AuthResponse.of(jwtService.generateAccessToken(user), user, organization);
    }


    @Transactional(noRollbackFor = {InvalidCredentialsException.class, AccountLockedException.class})
    public AuthResponse login(LoginRequest request) throws Exception {
        String email = normalizeEmail(request.email());

        Optional<User> found = userRepository.findByEmailForUpdate(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }
        User user = found.get();
        Instant now = Instant.now();

        if (user.getLockedUntil() != null) {
            if (now.isBefore(user.getLockedUntil())) {
                throw new AccountLockedException();
            }
            user.setLockedUntil(null);
            user.setFailedLoginAttempts(0);
        }

        if (!passwordMatches(request.password(), user.getPasswordHash())) {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= maxFailedLoginAttempts) {
                user.setLockedUntil(now.plus(loginLockDuration));
            }
            userRepository.saveAndFlush(user);
            throw failures >= maxFailedLoginAttempts ? new AccountLockedException() : new InvalidCredentialsException();
        }


        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        if (user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
            userRepository.saveAndFlush(user);
        }


        Organization organization = user.getRole() == Role.ORGANIZATION
                ? organizationRepository.findByUserId(user.getId()).orElse(null)
                : null;

        return AuthResponse.of(jwtService.generateAccessToken(user), user, organization);
    }

    private boolean passwordMatches(String rawPassword, String storedHash) {
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            passwordEncoder.matches("x", dummyHash); // keep timing uniform
            return false;
        }
        return passwordEncoder.matches(rawPassword, storedHash);
    }



    private PendingRegistration findOrCreatePending(String email) {
        return pendingRegistrationRepository.findByEmailForUpdate(email).orElseGet(PendingRegistration::new);
    }


    private void assertResendAllowed(PendingRegistration pending) {
        if (pending.getOtpHash() == null) {
            return;
        }
        Instant issuedAt = pending.getOtpExpiresAt().minus(otpService.ttl());
        if (Instant.now().isBefore(issuedAt.plus(RESEND_COOLDOWN))) {
            throw new OtpAlreadySentException(
                    "A code was requested recently. Please wait a minute before trying again.");
        }
    }

    private static DeliveryChannel resolveChannel(DeliveryChannel requested) {
        return requested == null ? DeliveryChannel.EMAIL : requested;
    }

    private void fillAccount(PendingRegistration pending, String fullName, String email, String phone,
                             String rawPassword, Role role, DeliveryChannel channel) {
        pending.setFullName(fullName.trim());
        pending.setEmail(email);
        pending.setPhone(phone);
        pending.setPasswordHash(passwordEncoder.encode(rawPassword));
        pending.setRole(role);
        pending.setChannel(channel);
    }

    private void clearOrganizationDetails(PendingRegistration pending) {
        pending.setOrganizationName(null);
        pending.setRegistrationNumber(null);
        pending.setDescription(null);
        pending.setServicesDescription(null);
        pending.setContactEmail(null);
        pending.setContactPhone(null);
        pending.setBusinessAddress(null);
    }

    private void assertAccountAvailable(String email, String phone) {
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email is already registered");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new DuplicateResourceException("Phone number is already registered");
        }
    }

    private void assertRegistrationNumberAvailable(String registrationNumber) {
        if (registrationNumber != null && organizationRepository.existsByRegistrationNumber(registrationNumber)) {
            throw new DuplicateResourceException("Registration number is already registered");
        }
    }

    private static String mask(String value, DeliveryChannel channel) {
        if (channel == DeliveryChannel.SMS) {
            int keep = Math.min(3, value.length());
            return "*".repeat(value.length() - keep) + value.substring(value.length() - keep);
        }
        int at = value.indexOf('@');
        return at <= 0 ? "***" : value.charAt(0) + "***" + value.substring(at);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // Blank -> null keeps empty strings from colliding on UNIQUE indexes.
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}