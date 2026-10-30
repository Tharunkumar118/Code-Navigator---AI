package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseColumnDto {
    private String columnName;
    private String fieldName;
    private String dataType;
    private Boolean nullable;
    private Boolean primaryKey;
    private Boolean foreignKey;
    private String referencedTable;
}
