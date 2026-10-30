package com.codenavigator.ai.repository;

import com.codenavigator.ai.entity.ActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop10ByOrderByCreatedAtDesc();
}
