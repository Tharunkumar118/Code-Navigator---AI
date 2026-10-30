package com.codenavigator.ai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityResultDto {
    private int vulnerabilitiesCount;
    private List<VulnerabilityDto> vulnerabilities;
    private String securitySummary;
}
