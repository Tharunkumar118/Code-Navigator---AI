package com.codenavigator.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class RepositorySummaryDto {
    private Long id;
    private String repoName;
    private String repoUrl;
    private String primaryLanguage;
    private String status;
    private Instant createdAt;
}
