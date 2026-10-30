package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.LoginRequest;
import com.codenavigator.ai.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void register_thenLogin_thenAccessProtectedProfile_fullFlowWorks() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setFullName("Aditi Sharma");
        register.setEmail("aditi@example.com");
        register.setPassword("SecurePass123");

        // 1. Register
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("aditi@example.com"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        // 2. Duplicate registration must fail with 409
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isConflict());

        // 3. Login
        LoginRequest login = new LoginRequest();
        login.setEmail("aditi@example.com");
        login.setPassword("SecurePass123");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).get("data").get("accessToken").asText();

        // 4. Without token -> protected endpoint rejects
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());

        // 5. With valid token -> protected endpoint succeeds
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("aditi@example.com"));
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        RegisterRequest register = new RegisterRequest();
        register.setFullName("Rahul Verma");
        register.setEmail("rahul@example.com");
        register.setPassword("CorrectPass123");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(register)))
                .andExpect(status().isCreated());

        LoginRequest badLogin = new LoginRequest();
        badLogin.setEmail("rahul@example.com");
        badLogin.setPassword("WrongPassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
