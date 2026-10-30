package com.codenavigator.ai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeQualityResultDto {
    private int qualityScore;
    private List<QualityIssueDto> issues;
    private String qualitySummary;
}
