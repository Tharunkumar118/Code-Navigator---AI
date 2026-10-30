package com.codenavigator.ai.exception;

/** Thrown during registration when the email is already taken. Mapped to HTTP 409. */
public class EmailAlreadyExistsException extends RuntimeException {
    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
