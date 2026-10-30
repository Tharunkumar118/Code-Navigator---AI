package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "code_quality_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeQualityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Column(name = "quality_score", nullable = false)
    private int qualityScore;

    @Lob
    @Column(name = "issues_json", columnDefinition = "LONGTEXT")
    private String issuesJson;

    @Lob
    @Column(name = "quality_summary", columnDefinition = "LONGTEXT")
    private String qualitySummary;
}
