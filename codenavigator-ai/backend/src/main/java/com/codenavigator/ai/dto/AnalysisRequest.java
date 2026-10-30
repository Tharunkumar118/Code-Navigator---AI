package com.codenavigator.ai.dto;

import com.codenavigator.ai.entity.AnalysisType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AnalysisRequest {

    @NotNull(message = "Repository ID is required")
    private Long repositoryId;

    @NotNull(message = "Analysis type is required")
    private AnalysisType type;
}
