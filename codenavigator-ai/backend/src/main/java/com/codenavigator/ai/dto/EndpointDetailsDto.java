package com.codenavigator.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EndpointDetailsDto {
    private String controllerName;
    private String methodName;
    private String httpMethod; // GET, POST, PUT, DELETE, PATCH
    private String path;
    
    @Builder.Default
    private List<EndpointParameterDto> parameters = new ArrayList<>();
    
    private String requestBodyType;
    private String responseType;
    private String summary;
}
