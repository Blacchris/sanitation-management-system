package com.sanitation.management.auth.service;

import com.sanitation.management.auth.dto.*;
import com.sanitation.management.auth.entity.DeliveryChannel;
import com.sanitation.management.auth.entity.OtpEmailOutbox;
import com.sanitation.management.auth.entity.PendingRegistration;
import com.sanitation.management.auth.entity.RegistrationType;
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


        pending.setRegistrationType(RegistrationType.CUSTOMER);
        pending.setFullName(request.fullName().trim());
        pending.setEmail(email);
        pending.setPhone(phone);
        pending.setPasswordHash(passwordEncoder.encode(request.password()));
        pending.setChannel(channel);
        clearOrganizationDetails(pending);

        return issueOtp(pending, channel);
    }

    @Transactional
    public RegistrationResponse registerOrganization(OrganizationRegistrationRequest request) {
        String email = normalizeEmail(request.contactEmail());
        String phone = request.contactPhone().trim();
        String registrationNumber = blankToNull(request.registrationNumber());
        DeliveryChannel channel = resolveChannel(request.otpChannel());

        assertOrganizationAvailable(email, phone, registrationNumber);

        PendingRegistration pending = findOrCreatePending(email);
        assertResendAllowed(pending);

        // Contact email/phone double as the OTP destination. No user account is involved.
        pending.setRegistrationType(RegistrationType.ORGANIZATION);
        pending.setEmail(email);
        pending.setPhone(phone);
        pending.setChannel(channel);
        pending.setFullName(null);
        pending.setPasswordHash(null);
        pending.setOrganizationName(request.organizationName().trim());
        pending.setRegistrationNumber(registrationNumber);
        pending.setDescription(blankToNull(request.description()));
        pending.setServicesDescription(request.servicesDescription().trim());
        pending.setBusinessAddress(request.businessAddress().trim());

        return issueOtp(pending, channel);
    }

    @Transactional
    public RegistrationResponse resendOtp(ResendOtpRequest request) {
        String email = normalizeEmail(request.email());

        PendingRegistration pending = pendingRegistrationRepository.findByEmailForUpdate(email)
                .orElseThrow(InvalidOtpException::new);

        assertResendAllowed(pending);

        return issueOtp(pending, pending.getChannel());
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

        //Explain why the OTPEmailOutbox row identity uses the PendingRegistration id
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
    public AuthResponse verifyCustomerRegistration(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        PendingRegistration pending = checkOtp(email, request.otp(), RegistrationType.CUSTOMER);

        assertAccountAvailable(email, pending.getPhone());

        User user = new User();
        user.setFullName(pending.getFullName());
        user.setEmail(pending.getEmail());
        user.setPhone(pending.getPhone());
        user.setPasswordHash(pending.getPasswordHash());
        user.setRole(Role.CUSTOMER);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.saveAndFlush(user);

        discardPending(pending);

        return AuthResponse.of(jwtService.generateAccessToken(user), user);
    }

    /** Organizations have no login, so verification creates the Organization only and issues no token. */
    @Transactional(noRollbackFor = {InvalidOtpException.class, TooManyAttemptsException.class})
    public OrganizationSummary verifyOrganizationRegistration(VerifyOtpRequest request) {
        String email = normalizeEmail(request.email());
        PendingRegistration pending = checkOtp(email, request.otp(), RegistrationType.ORGANIZATION);

        assertOrganizationAvailable(pending.getEmail(), pending.getPhone(), pending.getRegistrationNumber());

        Organization organization = new Organization();
        organization.setOrganizationName(pending.getOrganizationName());
        organization.setRegistrationNumber(pending.getRegistrationNumber());
        organization.setDescription(pending.getDescription());
        organization.setServicesDescription(pending.getServicesDescription());
        organization.setContactEmail(pending.getEmail());
        organization.setContactPhone(pending.getPhone());
        organization.setBusinessAddress(pending.getBusinessAddress());
        organization.setVerificationStatus(VerificationStatus.PENDING); // OTP != admin verification
        organization.setAcceptingRequests(Boolean.FALSE);
        organizationRepository.saveAndFlush(organization);

        discardPending(pending);

        return OrganizationSummary.from(organization);
    }

    /** Shared OTP check. A pending row of the other type is treated as "no such registration". */
    private PendingRegistration checkOtp(String email, String otp, RegistrationType expectedType) {
        PendingRegistration pending = pendingRegistrationRepository.findByEmailForUpdate(email)
                .orElseThrow(InvalidOtpException::new);

        if (pending.getRegistrationType() != expectedType) {
            throw new InvalidOtpException();
        }
        if (pending.getOtpAttempts() >= otpService.maxAttempts()) {
            throw new TooManyAttemptsException();
        }
        if (Instant.now().isAfter(pending.getOtpExpiresAt())) {
            throw new InvalidOtpException();
        }
        if (!otpService.matches(email, otp, pending.getOtpHash())) {
            int attempts = pending.getOtpAttempts() + 1;
            pending.setOtpAttempts(attempts);
            pendingRegistrationRepository.saveAndFlush(pending);
            throw attempts >= otpService.maxAttempts() ? new TooManyAttemptsException() : new InvalidOtpException();
        }
        return pending;
    }

    private void discardPending(PendingRegistration pending) {
        // Child (outbox) first, then parent (pending): the outbox PK is a foreign key to the pending row.
        otpEmailOutboxRepository.deleteById(pending.getId());
        pendingRegistrationRepository.delete(pending);
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


        return AuthResponse.of(jwtService.generateAccessToken(user), user);
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

    private void clearOrganizationDetails(PendingRegistration pending) {
        pending.setOrganizationName(null);
        pending.setRegistrationNumber(null);
        pending.setDescription(null);
        pending.setServicesDescription(null);
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

    private void assertOrganizationAvailable(String contactEmail, String contactPhone, String registrationNumber) {
        if (organizationRepository.existsByContactEmail(contactEmail)) {
            throw new DuplicateResourceException("Organization email is already registered");
        }
        if (organizationRepository.existsByContactPhone(contactPhone)) {
            throw new DuplicateResourceException("Organization phone number is already registered");
        }
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