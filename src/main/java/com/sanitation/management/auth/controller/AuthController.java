package com.sanitation.management.auth.controller;

import com.sanitation.management.auth.dto.*;
import com.sanitation.management.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AuthController {

    private final AuthService authService;

    // 202 Accepted: the request was accepted for processing; the account does not exist until the OTP is verified.
    @PostMapping("/register/customer")
    public ResponseEntity<RegistrationResponse> registerCustomer(
            @Valid @RequestBody CustomerRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.registerCustomer(request));
    }

    @PostMapping("/register/organization")
    public ResponseEntity<RegistrationResponse> registerOrganization(
            @Valid @RequestBody OrganizationRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.registerOrganization(request));
    }

    @PostMapping("/register/resend-otp")
    public ResponseEntity<RegistrationResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.resendOtp(request));
    }

    // Customer: creates the user and returns a token.
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.verifyCustomerRegistration(request));
    }

    // Organization: creates the organization only (no account, no token). Email = the contact email used to register.
    @PostMapping("/verify-otp/organization")
    public ResponseEntity<OrganizationSummary> verifyOrganizationOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.verifyOrganizationRegistration(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) throws Exception {
        return ResponseEntity.ok(authService.login(request));
    }
}