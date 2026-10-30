package com.codenavigator.ai.service;

import org.springframework.scheduling.annotation.Async;

public interface AnalysisExecutor {
    /**
     * Runs the analysis pipeline asynchronously. Matches the AnalysisType
     * and routes processing to the appropriate engine (e.g. ArchitectureAnalyzerService).
     *
     * @param analysisRecordId the ID of the AnalysisRecord status tracking database record
     */
    @Async
    void executeAnalysis(Long analysisRecordId);
}
