package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldDetailsDto {
    private String name;
    private String type;
    private String modifiers;
}
