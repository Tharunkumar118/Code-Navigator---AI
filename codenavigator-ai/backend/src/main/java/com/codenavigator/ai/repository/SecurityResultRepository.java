package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.SecurityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SecurityResultRepository extends JpaRepository<SecurityResult, Long> {
    Optional<SecurityResult> findByAnalysisRecordId(Long analysisRecordId);
}
