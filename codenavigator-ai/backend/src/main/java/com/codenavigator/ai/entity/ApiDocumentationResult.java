package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "api_documentation_results")
@Getter
@Setter
@NoArgsConstructor
public class ApiDocumentationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Column(nullable = false)
    private Integer endpointsCount = 0;

    @Column(columnDefinition = "LONGTEXT")
    private String openapiDoc;

    @Column(columnDefinition = "LONGTEXT")
    private String endpointsJson;
}
