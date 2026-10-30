package com.codenavigator.ai.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderNodeDto {
    private String name;
    private String type; // "file" or "directory"
    
    @Builder.Default
    private List<FolderNodeDto> children = new ArrayList<>();
}
