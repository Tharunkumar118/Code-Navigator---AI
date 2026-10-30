package com.codenavigator.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AdminDashboardResponse {
    private long totalUsers;
    private long totalStudents;
    private long totalFaculty;
    private long totalAdmins;

    private long totalRepositories;
    private long totalAnalyses;
    private long totalReports;

    private String systemStatus; // "UP" / "DOWN" - mirrors actuator health
    private String databaseStatus;

    private List<ActivityLogDto> recentActivity;
}
