package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.AdminDashboardResponse;
import com.codenavigator.ai.dto.ApiResponse;
import com.codenavigator.ai.dto.StudentDashboardResponse;
import com.codenavigator.ai.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * Reachable by any authenticated user (Student or Faculty).
     * Data is scoped to the authenticated user via `authentication.getName()` (their email) -
     * a student can never see another student's dashboard through this endpoint.
     */
    @GetMapping("/student")
    public ResponseEntity<ApiResponse<StudentDashboardResponse>> getStudentDashboard(Authentication authentication) {
        StudentDashboardResponse response = dashboardService.getStudentDashboard(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("Student dashboard fetched", response));
    }

    /**
     * @PreAuthorize enforces this at the method level - independent of SecurityConfig's
     * URL rules - so a Student/Faculty JWT is rejected with 403 even if they know the URL.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getAdminDashboard() {
        AdminDashboardResponse response = dashboardService.getAdminDashboard();
        return ResponseEntity.ok(ApiResponse.success("Admin dashboard fetched", response));
    }
}
