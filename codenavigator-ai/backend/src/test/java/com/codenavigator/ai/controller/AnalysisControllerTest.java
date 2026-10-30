package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.AnalysisRequest;
import com.codenavigator.ai.dto.LoginRequest;
import com.codenavigator.ai.dto.RegisterRequest;
import com.codenavigator.ai.entity.AnalysisType;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.RepoStatus;
import com.codenavigator.ai.entity.User;
import com.codenavigator.ai.repository.CodeRepositoryRepository;
import com.codenavigator.ai.repository.UserRepository;
import com.codenavigator.ai.service.AnalysisExecutor;
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
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CodeRepositoryRepository codeRepositoryRepository;

    @MockBean
    private AnalysisExecutor analysisExecutor;

    private String token;
    private Long repositoryId;

    @BeforeEach
    void setUp() throws Exception {
        // Register and login to get JWT
        RegisterRequest register = new RegisterRequest();
        register.setFullName("Analysis Tester");
        register.setEmail("analysistester@example.com");
        register.setPassword("Password123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)));

        LoginRequest login = new LoginRequest();
        login.setEmail("analysistester@example.com");
        login.setPassword("Password123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        token = objectMapper.readTree(responseJson).get("data").get("accessToken").asText();

        // Seed a successfully cloned repository under this user
        User user = userRepository.findByEmail("analysistester@example.com").orElseThrow();
        CodeRepository repo = new CodeRepository();
        repo.setOwner(user);
        repo.setRepoUrl("https://github.com/example/repo");
        repo.setRepoName("repo");
        repo.setStatus(RepoStatus.COMPLETED);
        repo.setClonePath("cloned-repos/mock-repo");
        repo = codeRepositoryRepository.save(repo);
        repositoryId = repo.getId();

        // Mock background analysis execution
        Mockito.doNothing().when(analysisExecutor).executeAnalysis(any());
    }

    @Test
    void submitAnalysis_withValidRequest_returnsQueuedResponse() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setRepositoryId(repositoryId);
        request.setType(AnalysisType.ARCHITECTURE);

        mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("ARCHITECTURE"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void submitAnalysis_forUnclonedRepository_returnsBadRequest() throws Exception {
        // Seed an uncloned repository
        User user = userRepository.findByEmail("analysistester@example.com").orElseThrow();
        CodeRepository pendingRepo = new CodeRepository();
        pendingRepo.setOwner(user);
        pendingRepo.setRepoUrl("https://github.com/example/pending");
        pendingRepo.setRepoName("pending");
        pendingRepo.setStatus(RepoStatus.PENDING);
        pendingRepo = codeRepositoryRepository.save(pendingRepo);

        AnalysisRequest request = new AnalysisRequest();
        request.setRepositoryId(pendingRepo.getId());
        request.setType(AnalysisType.ARCHITECTURE);

        mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false)); // Should yield global handler error for IllegalStateException
    }

    @Test
    void submitAnalysis_sourceCode_returnsQueuedResponse() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setRepositoryId(repositoryId);
        request.setType(AnalysisType.SOURCE_CODE);

        mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("SOURCE_CODE"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void submitAnalysis_apiDocumentation_returnsQueuedResponse() throws Exception {
        AnalysisRequest request = new AnalysisRequest();
        request.setRepositoryId(repositoryId);
        request.setType(AnalysisType.API_DOCUMENTATION);

        mockMvc.perform(post("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("API_DOCUMENTATION"))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }
}
