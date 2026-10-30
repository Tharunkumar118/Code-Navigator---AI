package com.codenavigator.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RepositoryRequest {

    @NotBlank(message = "Repository URL is required")
    @Pattern(
        regexp = "^(https?://)?(www\\.)?github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+(\\.git)?/?$",
        message = "Invalid GitHub repository URL. Must be a public repository URL (e.g. https://github.com/owner/repo)"
    )
    private String repoUrl;
}
