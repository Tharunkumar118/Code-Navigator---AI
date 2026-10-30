package com.codenavigator.ai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassDetailsDto {
    private String name;
    private String fullyQualifiedName;
    private String type; // "class", "interface", "enum", "record"
    private Boolean isInterface;
    private Boolean isAbstract;
    private String superClass;
    private List<String> implementedInterfaces;
    private List<FieldDetailsDto> fields;
    private List<MethodDetailsDto> methods;
    private Integer linesOfCode;
    private Integer cyclomaticComplexity;
}
