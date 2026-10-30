package com.codenavigator.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepositoryResponse {
    private Long id;
    private String repoUrl;
    private String repoName;
    private String primaryLanguage;
    private String framework;
    private Long sizeKb;
    private List<String> dependencies;
    private FolderNodeDto folderStructure;
    private String buildTool;
    private List<String> modules;
    private List<String> configurationFiles;
    private List<String> controllers;
    private List<String> services;
    private List<String> repositories;
    private List<String> entities;
    private List<String> utilities;
    private String status;
    private Instant createdAt;
}
