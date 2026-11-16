package com.sanitation.management.common.exception;

public class AccountNotActiveException extends RuntimeException{

    public AccountNotActiveException() {

    }
    public AccountNotActiveException(String message) {
        super(message);
    }
}
