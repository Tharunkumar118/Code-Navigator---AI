package com.codenavigator.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InheritanceNodeDto {
    private String className;
    private String superClass;
    
    @Builder.Default
    private List<String> subClasses = new ArrayList<>();
}
