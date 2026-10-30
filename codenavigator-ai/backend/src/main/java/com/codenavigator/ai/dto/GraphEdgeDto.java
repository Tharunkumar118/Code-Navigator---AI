package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphEdgeDto {
    private String source;
    private String target;
    private String type; // e.g. "dependency", "calls", "contains"
}
