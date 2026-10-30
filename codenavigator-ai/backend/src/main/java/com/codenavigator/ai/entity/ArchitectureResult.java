package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "architecture_results")
@Getter
@Setter
@NoArgsConstructor
public class ArchitectureResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Column(columnDefinition = "LONGTEXT")
    private String projectStructure;

    @Column(columnDefinition = "LONGTEXT")
    private String packageStructure;

    @Column(columnDefinition = "LONGTEXT")
    private String dependencyGraph;

    @Column(columnDefinition = "LONGTEXT")
    private String moduleGraph;

    @Column(columnDefinition = "LONGTEXT")
    private String callGraph;

    @Column(columnDefinition = "LONGTEXT")
    private String architectureSummary;
}
