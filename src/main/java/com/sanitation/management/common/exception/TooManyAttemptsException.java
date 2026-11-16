package com.sanitation.management.common.exception;

public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException() {
        super("Too many incorrect attempts. Please register again to receive a new code");
    }
}
