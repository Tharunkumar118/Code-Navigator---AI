package com.codenavigator.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArchitectureResultDto {
    private String projectStructure;
    private FolderNodeDto packageStructure;
    private GraphDto dependencyGraph;
    private GraphDto moduleGraph;
    private GraphDto callGraph;
    private String architectureSummary;
    private DiagramResultDto diagrams;
}
