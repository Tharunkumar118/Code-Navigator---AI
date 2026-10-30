package com.codenavigator.ai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodDetailsDto {
    private String name;
    private String returnType;
    private List<String> parameters;
    private String modifiers;
    private Integer linesOfCode;
    private Integer cyclomaticComplexity;
}
