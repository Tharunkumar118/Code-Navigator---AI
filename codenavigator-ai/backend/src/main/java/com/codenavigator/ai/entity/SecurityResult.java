package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "security_results")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @Column(name = "vulnerabilities_count", nullable = false)
    private int vulnerabilitiesCount;

    @Lob
    @Column(name = "vulnerabilities_json", columnDefinition = "LONGTEXT")
    private String vulnerabilitiesJson;

    @Lob
    @Column(name = "security_summary", columnDefinition = "LONGTEXT")
    private String securitySummary;
}
