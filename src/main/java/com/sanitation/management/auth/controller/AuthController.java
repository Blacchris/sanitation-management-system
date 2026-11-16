package com.sanitation.management.auth.controller;

import com.sanitation.management.auth.dto.AuthResponse;
import com.sanitation.management.auth.dto.CustomerRegistrationRequest;
import com.sanitation.management.auth.dto.LoginRequest;
import com.sanitation.management.auth.dto.OrganizationRegistrationRequest;
import com.sanitation.management.auth.dto.RegistrationResponse;
import com.sanitation.management.auth.dto.VerifyOtpRequest;
import com.sanitation.management.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
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

    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.verifyRegistration(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) throws Exception {
        return ResponseEntity.ok(authService.login(request));
    }
}