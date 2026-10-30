package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.ApiDocumentationResult;
import com.codenavigator.ai.entity.AnalysisRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ApiDocumentationResultRepository extends JpaRepository<ApiDocumentationResult, Long> {

    /**
     * Finds the API documentation result associated with a specific analysis record.
     *
     * @param record the parent AnalysisRecord
     * @return an optional containing the result if found
     */
    Optional<ApiDocumentationResult> findByAnalysisRecord(AnalysisRecord record);

    /**
     * Finds the API documentation result by the ID of the parent analysis record.
     *
     * @param recordId the ID of the parent AnalysisRecord
     * @return an optional containing the result if found
     */
    Optional<ApiDocumentationResult> findByAnalysisRecordId(Long recordId);
}
