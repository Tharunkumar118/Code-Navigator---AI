package com.codenavigator.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiDocumentationResultDto {
    private Integer endpointsCount;
    
    @Builder.Default
    private List<EndpointDetailsDto> endpoints = new ArrayList<>();
    
    private Map<String, Object> openapiDoc; // parsed OpenAPI JSON specification
}
