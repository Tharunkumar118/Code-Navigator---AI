package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.AuthResponse;
import com.codenavigator.ai.dto.LoginRequest;
import com.codenavigator.ai.dto.RegisterRequest;
import com.codenavigator.ai.dto.UserProfileResponse;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    UserProfileResponse getCurrentUserProfile(String email);
}
