package com.codenavigator.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {
    private Long id;
    private Long repositoryId;
    private String repositoryName;
    private String type;
    private String status;
    private Instant createdAt;
    private Instant completedAt;
    private Object result;
}
