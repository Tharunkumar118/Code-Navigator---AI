package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.LoginRequest;
import com.codenavigator.ai.dto.RegisterRequest;
import com.codenavigator.ai.dto.RepositoryRequest;
import com.codenavigator.ai.service.GitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GitService gitService;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        // Register and login to get JWT
        RegisterRequest register = new RegisterRequest();
        register.setFullName("Test User");
        register.setEmail("testuser@example.com");
        register.setPassword("Password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)));

        LoginRequest login = new LoginRequest();
        login.setEmail("testuser@example.com");
        login.setPassword("Password123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        token = objectMapper.readTree(responseJson).get("data").get("accessToken").asText();

        // Stub the git clone service to do nothing (mocked success)
        Mockito.doNothing().when(gitService).cloneRepository(any(), any());
    }

    @Test
    void submitRepository_withValidUrl_createsPendingRepo() throws Exception {
        RepositoryRequest request = new RepositoryRequest();
        request.setRepoUrl("https://github.com/octocat/Spoon-Knife");

        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.repoName").value("Spoon-Knife"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void submitRepository_withInvalidUrl_returnsBadRequest() throws Exception {
        RepositoryRequest request = new RepositoryRequest();
        request.setRepoUrl("https://notgithub.com/invalid/url");

        mockMvc.perform(post("/api/v1/repositories")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void getRepository_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/1"))
                .andExpect(status().isUnauthorized());
    }
}
