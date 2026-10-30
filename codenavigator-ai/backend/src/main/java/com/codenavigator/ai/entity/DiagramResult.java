package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "diagram_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiagramResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Lob
    @Column(name = "class_diagram", columnDefinition = "LONGTEXT")
    private String classDiagram;

    @Lob
    @Column(name = "package_diagram", columnDefinition = "LONGTEXT")
    private String packageDiagram;

    @Lob
    @Column(name = "component_diagram", columnDefinition = "LONGTEXT")
    private String componentDiagram;

    @Lob
    @Column(name = "sequence_diagram", columnDefinition = "LONGTEXT")
    private String sequenceDiagram;

    @Lob
    @Column(name = "use_case_diagram", columnDefinition = "LONGTEXT")
    private String useCaseDiagram;

    @Lob
    @Column(name = "architecture_diagram", columnDefinition = "LONGTEXT")
    private String architectureDiagram;
}
