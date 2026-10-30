package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "database_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatabaseResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Column(name = "entities_count", nullable = false)
    private int entitiesCount;

    @Column(name = "relationships_count", nullable = false)
    private int relationshipsCount;

    @Lob
    @Column(name = "entities_json", columnDefinition = "LONGTEXT")
    private String entitiesJson;

    @Lob
    @Column(name = "er_diagram_mermaid", columnDefinition = "LONGTEXT")
    private String erDiagramMermaid;

    @Lob
    @Column(name = "database_summary", columnDefinition = "LONGTEXT")
    private String databaseSummary;
}
