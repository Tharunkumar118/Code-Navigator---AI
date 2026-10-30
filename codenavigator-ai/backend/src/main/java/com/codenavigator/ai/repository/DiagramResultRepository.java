package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.DiagramResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiagramResultRepository extends JpaRepository<DiagramResult, Long> {
    Optional<DiagramResult> findByAnalysisRecordId(Long analysisRecordId);
}
