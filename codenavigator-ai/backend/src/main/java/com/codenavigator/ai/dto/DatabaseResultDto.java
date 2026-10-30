package com.codenavigator.ai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseResultDto {
    private int entitiesCount;
    private int relationshipsCount;
    private List<DatabaseEntityDto> entities;
    private String erDiagramMermaid;
    private String databaseSummary;
}
