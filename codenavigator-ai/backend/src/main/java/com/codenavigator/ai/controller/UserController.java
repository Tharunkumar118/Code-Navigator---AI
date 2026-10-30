package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.ApiResponse;
import com.codenavigator.ai.dto.UserProfileResponse;
import com.codenavigator.ai.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;

    /**
     * `Authentication` is injected directly by Spring MVC - it comes from the
     * SecurityContext that JwtAuthFilter populated for this request.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(Authentication authentication) {
        String email = authentication.getName();
        UserProfileResponse profile = authService.getCurrentUserProfile(email);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched", profile));
    }
}
