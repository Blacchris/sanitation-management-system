package com.sanitation.management.common.exception;

/** One message for "unknown email" and "wrong password": the caller must not be able to tell them apart. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}