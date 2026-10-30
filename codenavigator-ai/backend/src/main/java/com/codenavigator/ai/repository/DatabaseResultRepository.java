package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.DatabaseResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DatabaseResultRepository extends JpaRepository<DatabaseResult, Long> {
    Optional<DatabaseResult> findByAnalysisRecordId(Long analysisRecordId);
}
