package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.AdminDashboardResponse;
import com.codenavigator.ai.dto.StudentDashboardResponse;

public interface DashboardService {

    StudentDashboardResponse getStudentDashboard(String email);

    AdminDashboardResponse getAdminDashboard();
}
