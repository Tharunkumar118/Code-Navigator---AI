package com.codenavigator.ai.service;

import org.springframework.scheduling.annotation.Async;

public interface RepositoryAnalyzer {
    /**
     * Performs the asynchronous cloning and scanning of a repository.
     *
     * @param repositoryId the ID of the repository database record to process
     */
    @Async
    void cloneAndAnalyze(Long repositoryId);
}
