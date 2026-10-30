package com.codenavigator.ai.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointParameterDto {
    private String name;
    private String type;
    private String in; // "path", "query", "header", "body"
    private Boolean required;
    private String defaultValue;
}
