package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.Report;
import com.codenavigator.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

    long countByGeneratedFor(User user);
}
