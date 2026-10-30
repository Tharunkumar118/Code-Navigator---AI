package com.codenavigator.ai.entity;

/**
 * Fixed set of roles in the system. Kept as an enum (not free-text strings)
 * so invalid role names are impossible at the type level.
 */
public enum ERole {
    ROLE_STUDENT,
    ROLE_FACULTY,
    ROLE_ADMIN
}
