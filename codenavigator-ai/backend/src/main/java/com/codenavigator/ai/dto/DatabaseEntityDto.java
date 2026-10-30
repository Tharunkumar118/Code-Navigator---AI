package com.codenavigator.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseEntityDto {
    private String tableName;
    private String className;
    
    @Builder.Default
    private List<DatabaseColumnDto> columns = new ArrayList<>();
    
    @Builder.Default
    private List<String> primaryKeys = new ArrayList<>();
    
    @Builder.Default
    private List<String> foreignKeys = new ArrayList<>();
    
    @Builder.Default
    private List<String> indexes = new ArrayList<>();
}
