package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.AnalysisRequest;
import com.codenavigator.ai.dto.AnalysisResponse;
import java.util.List;

public interface AnalysisService {
    /**
     * Submits a repository for a specific analysis type (e.g. ARCHITECTURE).
     *
     * @param request   payload containing repository ID and analysis type
     * @param userEmail email of the authenticated user
     * @return the initial AnalysisResponse carrying a status of QUEUED
     */
    AnalysisResponse submitAnalysis(AnalysisRequest request, String userEmail);

    /**
     * Retrieves the status and detailed results of an analysis.
     *
     * @param id        the analysis record ID
     * @param userEmail email of the authenticated user (for validation)
     * @return detailed AnalysisResponse mapping status, timeline, and results
     */
    AnalysisResponse getAnalysis(Long id, String userEmail);

    /**
     * Lists the analysis history of a specific repository.
     *
     * @param repositoryId the ID of the repository
     * @param userEmail    email of the authenticated user
     * @return list of analysis response objects
     */
    List<AnalysisResponse> getAnalysisHistory(Long repositoryId, String userEmail);
}
