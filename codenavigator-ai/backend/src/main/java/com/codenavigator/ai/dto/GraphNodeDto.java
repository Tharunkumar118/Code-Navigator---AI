package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphNodeDto {
    private String id;
    private String label;
    private String type; // e.g. "class", "package", "method"
}
