package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "source_code_results")
@Getter
@Setter
@NoArgsConstructor
public class SourceCodeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Column(nullable = false)
    private Integer classesCount = 0;

    @Column(nullable = false)
    private Integer interfacesCount = 0;

    @Column(nullable = false)
    private Integer methodsCount = 0;

    @Column(nullable = false)
    private Integer totalLinesOfCode = 0;

    @Column(nullable = false)
    private Double averageCyclomaticComplexity = 0.0;

    @Column(columnDefinition = "LONGTEXT")
    private String classDetails;

    @Column(columnDefinition = "LONGTEXT")
    private String inheritanceTree;
}
