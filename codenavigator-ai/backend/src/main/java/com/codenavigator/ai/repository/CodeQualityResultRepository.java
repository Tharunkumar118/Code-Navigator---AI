package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.CodeQualityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodeQualityResultRepository extends JpaRepository<CodeQualityResult, Long> {
    Optional<CodeQualityResult> findByAnalysisRecordId(Long analysisRecordId);
}
