package com.sanitation.management.common.exception;

/** Deliberately vague: wrong code, expired code and unknown email all look the same to the caller. */
public class InvalidOtpException extends RuntimeException {

    public InvalidOtpException() {
        super("Invalid or expired verification code");
    }
}
