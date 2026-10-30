package com.codenavigator.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AuthResponse {

    private Long id;
    private String fullName;
    private String email;
    private List<String> roles;

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";
}
