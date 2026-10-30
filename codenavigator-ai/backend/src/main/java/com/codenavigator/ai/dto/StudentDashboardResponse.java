package com.codenavigator.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class StudentDashboardResponse {
    private List<RepositorySummaryDto> recentRepositories;
    private long totalRepositories;
    private long totalAnalyses;
    private long totalReports;
    // AI usage is wired to real numbers starting Module 13 (AI Project Understanding)
    private long aiQueriesUsed;
}
