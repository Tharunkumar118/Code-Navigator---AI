package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.RepositoryRequest;
import com.codenavigator.ai.dto.RepositoryResponse;
import java.util.List;

public interface RepositoryService {
    /**
     * Submits a repository URL for asynchronous cloning and scanning.
     *
     * @param request   payload containing repository URL
     * @param userEmail the email of the user requesting the analysis
     * @return a response DTO carrying the repository record in PENDING or CLONING state
     */
    RepositoryResponse submitRepository(RepositoryRequest request, String userEmail);

    /**
     * Retrieves the details of a repository.
     *
     * @param id        the repository database ID
     * @param userEmail the email of the user requesting the details (for validation)
     * @return the repository details response DTO
     */
    RepositoryResponse getRepository(Long id, String userEmail);

    /**
     * Lists all repositories analyzed or being analyzed by the user.
     *
     * @param userEmail the email of the user
     * @return list of repository response DTOs
     */
    List<RepositoryResponse> getRepositories(String userEmail);
}
