package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.LoginRequest;
import com.codenavigator.ai.dto.RegisterRequest;
import com.codenavigator.ai.entity.ERole;
import com.codenavigator.ai.entity.Role;
import com.codenavigator.ai.entity.User;
import com.codenavigator.ai.repository.RoleRepository;
import com.codenavigator.ai.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String registerAndGetToken(String name, String email, Set<String> roles) throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setFullName(name);
        register.setEmail(email);
        register.setPassword("SecurePass123");
        register.setRoles(roles);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    /** Simulates the real-world provisioning path: an Admin is created directly in the DB, not via public API. */
    private String seedAdminAndLogin(String email) throws Exception {
        Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN).orElseThrow();

        User admin = new User();
        admin.setFullName("System Admin");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode("AdminPass123"));
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("AdminPass123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).get("data").get("accessToken").asText();
    }

    @Test
    void adminDashboard_withAdminToken_succeedsAndReturnsStatistics() throws Exception {
        registerAndGetToken("Some Student", "student.forcount@example.com", null);
        String adminToken = seedAdminAndLogin("admin@example.com");

        mockMvc.perform(get("/api/v1/dashboard/admin")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalUsers").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.data.databaseStatus").value("UP"))
                .andExpect(jsonPath("$.data.recentActivity").isArray());
    }

    @Test
    void studentDashboard_returnsZeroedCountsForNewUser() throws Exception {
        String token = registerAndGetToken("Priya Patel", "priya@example.com", null);

        mockMvc.perform(get("/api/v1/dashboard/student")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRepositories").value(0))
                .andExpect(jsonPath("$.data.totalAnalyses").value(0))
                .andExpect(jsonPath("$.data.totalReports").value(0))
                .andExpect(jsonPath("$.data.recentRepositories").isArray());
    }

    @Test
    void studentDashboard_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/student"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminDashboard_rejectsStudentToken_withForbidden() throws Exception {
        String studentToken = registerAndGetToken("Karan Singh", "karan@example.com", null);

        mockMvc.perform(get("/api/v1/dashboard/admin")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminDashboard_allowsFacultyRegisteredButStillRejectsNonAdmin() throws Exception {
        String facultyToken = registerAndGetToken("Dr. Mehta", "mehta@example.com", Set.of("FACULTY"));

        mockMvc.perform(get("/api/v1/dashboard/admin")
                        .header("Authorization", "Bearer " + facultyToken))
                .andExpect(status().isForbidden());
    }
}
