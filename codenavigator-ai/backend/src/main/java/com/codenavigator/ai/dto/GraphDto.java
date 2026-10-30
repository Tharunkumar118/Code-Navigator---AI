package com.codenavigator.ai.dto;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphDto {
    @Builder.Default
    private List<GraphNodeDto> nodes = new ArrayList<>();
    
    @Builder.Default
    private List<GraphEdgeDto> edges = new ArrayList<>();
}
