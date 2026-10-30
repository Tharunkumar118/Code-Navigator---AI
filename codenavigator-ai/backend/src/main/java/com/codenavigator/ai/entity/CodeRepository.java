package com.codenavigator.ai.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Named CodeRepository (not "Repository") to avoid colliding with Spring Data's
 * own org.springframework.data.repository.Repository marker interface.
 *
 * Populated by Module 4 (GitHub Integration). Only basic fields exist now -
 * language/framework/size are nullable until the scanner (Module 5) fills them in.
 */
@Entity
@Table(name = "code_repositories")
@Getter
@Setter
@NoArgsConstructor
public class CodeRepository {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 500)
    private String repoUrl;

    @Column(length = 150)
    private String repoName;

    @Column(length = 50)
    private String primaryLanguage;

    @Column(length = 50)
    private String framework;

    private Long sizeKb;

    @Column(columnDefinition = "LONGTEXT")
    private String dependencies;

    @Column(columnDefinition = "LONGTEXT")
    private String folderStructure;

    @Column(length = 500)
    private String clonePath;

    @Column(length = 50)
    private String buildTool;

    @Column(columnDefinition = "LONGTEXT")
    private String modules;

    @Column(columnDefinition = "LONGTEXT")
    private String configurationFiles;

    @Column(columnDefinition = "LONGTEXT")
    private String controllers;

    @Column(columnDefinition = "LONGTEXT")
    private String services;

    @Column(columnDefinition = "LONGTEXT")
    private String repositories;

    @Column(columnDefinition = "LONGTEXT")
    private String entities;

    @Column(columnDefinition = "LONGTEXT")
    private String utilities;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RepoStatus status = RepoStatus.PENDING;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;
}
