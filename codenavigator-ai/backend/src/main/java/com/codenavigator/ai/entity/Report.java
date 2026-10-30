package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Populated starting Module 14 (Report Generator). */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analysis_record_id", nullable = false)
    private AnalysisRecord analysisRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_for", nullable = false)
    private User generatedFor;

    @Column(length = 30, nullable = false)
    private String reportType; // e.g. PDF, HTML - kept as String; formalized in Module 14

    @Column(length = 500)
    private String filePath;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
