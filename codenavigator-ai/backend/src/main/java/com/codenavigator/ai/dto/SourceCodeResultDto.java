package com.codenavigator.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceCodeResultDto {
    private Integer classesCount;
    private Integer interfacesCount;
    private Integer methodsCount;
    private Integer totalLinesOfCode;
    private Double averageCyclomaticComplexity;
    
    @Builder.Default
    private List<ClassDetailsDto> classDetails = new ArrayList<>();
    
    @Builder.Default
    private List<InheritanceNodeDto> inheritanceTree = new ArrayList<>();
}
