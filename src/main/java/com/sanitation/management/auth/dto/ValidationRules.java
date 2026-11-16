package com.sanitation.management.auth.dto;

final class ValidationRules {

    static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)[\\x20-\\x7E]{8,72}$";
    static final String PASSWORD_MESSAGE =
            "must be 8-72 characters (letters, digits, symbols) with at least one uppercase letter, one lowercase letter and one digit";

    static final String PHONE_REGEX = "^\\+?[0-9]{7,15}$";
    static final String PHONE_MESSAGE = "must be 7-15 digits, optionally starting with +";

    private ValidationRules() {
    }
}
