package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRecordRepository extends JpaRepository<AnalysisRecord, Long> {

    long countByRequestedBy(User user);

    java.util.List<AnalysisRecord> findByRepositoryIdOrderByCreatedAtDesc(Long repositoryId);
}
