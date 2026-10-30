package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityIssueDto {
    private String filePath;
    private int line;
    private String ruleName;
    private String severity; // INFO, WARNING, CRITICAL
    private String description;
    private String snippet;
}
