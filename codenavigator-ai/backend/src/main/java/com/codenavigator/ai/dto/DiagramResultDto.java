package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramResultDto {
    private String classDiagram;
    private String packageDiagram;
    private String componentDiagram;
    private String sequenceDiagram;
    private String useCaseDiagram;
    private String architectureDiagram;
}
