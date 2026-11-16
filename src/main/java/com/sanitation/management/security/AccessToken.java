package com.sanitation.management.security;

public record AccessToken(String value, long expiresInSeconds) {
}
