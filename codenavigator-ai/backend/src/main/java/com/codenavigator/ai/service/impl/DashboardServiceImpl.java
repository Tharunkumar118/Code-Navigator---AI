package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.*;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.ERole;
import com.codenavigator.ai.entity.User;
import com.codenavigator.ai.exception.ResourceNotFoundException;
import com.codenavigator.ai.repository.*;
import com.codenavigator.ai.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final int RECENT_REPOS_LIMIT = 5;

    private final UserRepository userRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final AnalysisRecordRepository analysisRecordRepository;
    private final ReportRepository reportRepository;
    private final ActivityLogRepository activityLogRepository;
    private final DataSource dataSource;

    @Override
    public StudentDashboardResponse getStudentDashboard(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));

        Pageable recentLimit = PageRequest.of(0, RECENT_REPOS_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<CodeRepository> recent = codeRepositoryRepository.findByOwnerOrderByCreatedAtDesc(user, recentLimit);

        List<RepositorySummaryDto> recentDtos = recent.stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return StudentDashboardResponse.builder()
                .recentRepositories(recentDtos)
                .totalRepositories(codeRepositoryRepository.countByOwner(user))
                .totalAnalyses(analysisRecordRepository.countByRequestedBy(user))
                .totalReports(reportRepository.countByGeneratedFor(user))
                .aiQueriesUsed(0L) // wired to real data in Module 13
                .build();
    }

    @Override
    public AdminDashboardResponse getAdminDashboard() {
        List<ActivityLogDto> recentActivity = activityLogRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(log -> ActivityLogDto.builder()
                        .userEmail(log.getUser().getEmail())
                        .action(log.getAction())
                        .description(log.getDescription())
                        .createdAt(log.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return AdminDashboardResponse.builder()
                .totalUsers(userRepository.count())
                .totalStudents(userRepository.countByRoles_Name(ERole.ROLE_STUDENT))
                .totalFaculty(userRepository.countByRoles_Name(ERole.ROLE_FACULTY))
                .totalAdmins(userRepository.countByRoles_Name(ERole.ROLE_ADMIN))
                .totalRepositories(codeRepositoryRepository.count())
                .totalAnalyses(analysisRecordRepository.count())
                .totalReports(reportRepository.count())
                .systemStatus("UP") // we're serving this response, so the app itself is up
                .databaseStatus(checkDatabaseStatus())
                .recentActivity(recentActivity)
                .build();
    }

    private String checkDatabaseStatus() {
        // Lightweight DB reachability check, same idea as Actuator's DB health indicator.
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (SQLException e) {
            return "DOWN";
        }
    }

    private RepositorySummaryDto toSummary(CodeRepository repo) {
        return RepositorySummaryDto.builder()
                .id(repo.getId())
                .repoName(repo.getRepoName())
                .repoUrl(repo.getRepoUrl())
                .primaryLanguage(repo.getPrimaryLanguage())
                .status(repo.getStatus().name())
                .createdAt(repo.getCreatedAt())
                .build();
    }
}
