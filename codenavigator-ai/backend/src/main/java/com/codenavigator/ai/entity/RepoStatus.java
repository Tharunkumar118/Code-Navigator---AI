package com.codenavigator.ai.entity;

/** Lifecycle state of a submitted repository. Written to by Module 4 (GitHub Integration). */
public enum RepoStatus {
    PENDING,
    CLONING,
    ANALYZING,
    COMPLETED,
    FAILED
}
