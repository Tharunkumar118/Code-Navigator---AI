package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.FolderNodeDto;
import com.codenavigator.ai.dto.RepositoryRequest;
import com.codenavigator.ai.dto.RepositoryResponse;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.RepoStatus;
import com.codenavigator.ai.entity.User;
import com.codenavigator.ai.exception.ResourceNotFoundException;
import com.codenavigator.ai.repository.CodeRepositoryRepository;
import com.codenavigator.ai.repository.UserRepository;
import com.codenavigator.ai.service.RepositoryAnalyzer;
import com.codenavigator.ai.service.RepositoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryServiceImpl implements RepositoryService {

    private final UserRepository userRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final RepositoryAnalyzer repositoryAnalyzer;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RepositoryResponse submitRepository(RepositoryRequest request, String userEmail) {
        log.info("User {} submitting repository URL: {}", userEmail, request.getRepoUrl());
        
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        String repoName = parseRepoName(request.getRepoUrl());

        CodeRepository repo = new CodeRepository();
        repo.setOwner(owner);
        repo.setRepoUrl(request.getRepoUrl());
        repo.setRepoName(repoName);
        repo.setStatus(RepoStatus.PENDING);

        repo = codeRepositoryRepository.save(repo);

        // Trigger the asynchronous background cloning & analysis
        repositoryAnalyzer.cloneAndAnalyze(repo.getId());

        return mapToResponse(repo);
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryResponse getRepository(Long id, String userEmail) {
        log.debug("Fetching repository ID: {} requested by user: {}", id, userEmail);
        
        CodeRepository repo = codeRepositoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", id));

        // Security check: only allow the owner to access the repository metadata
        if (!repo.getOwner().getEmail().equals(userEmail)) {
            log.warn("Access denied for user {} attempting to view repository ID {}", userEmail, id);
            throw new AccessDeniedException("You do not have permission to view this repository");
        }

        return mapToResponse(repo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryResponse> getRepositories(String userEmail) {
        log.debug("Listing all repositories for user: {}", userEmail);
        
        User owner = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        // Use custom Spring Data JPA repository query to fetch by owner, ordered by creation time
        List<CodeRepository> repos = codeRepositoryRepository.findByOwnerOrderByCreatedAtDesc(owner, org.springframework.data.domain.Pageable.unpaged());

        return repos.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private String parseRepoName(String url) {
        if (url == null || url.isBlank()) {
            return "unknown";
        }
        String clean = url.trim();
        // Remove trailing slashes
        while (clean.endsWith("/")) {
            clean = clean.substring(0, clean.length() - 1);
        }
        // Remove trailing .git extension
        if (clean.endsWith(".git")) {
            clean = clean.substring(0, clean.length() - 4);
        }
        // Grab segment after the last slash
        int lastSlash = clean.lastIndexOf('/');
        if (lastSlash >= 0) {
            return clean.substring(lastSlash + 1);
        }
        return clean;
    }

    private List<String> deserializeList(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize JSON list", e);
            return Collections.emptyList();
        }
    }

    private RepositoryResponse mapToResponse(CodeRepository repo) {
        List<String> deps = Collections.emptyList();
        if (repo.getDependencies() != null) {
            try {
                deps = objectMapper.readValue(repo.getDependencies(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.error("Failed to map dependencies JSON for repo ID: {}", repo.getId(), e);
            }
        }

        FolderNodeDto folderTree = null;
        if (repo.getFolderStructure() != null) {
            try {
                folderTree = objectMapper.readValue(repo.getFolderStructure(), FolderNodeDto.class);
            } catch (Exception e) {
                log.error("Failed to map folder structure JSON for repo ID: {}", repo.getId(), e);
            }
        }

        return RepositoryResponse.builder()
                .id(repo.getId())
                .repoUrl(repo.getRepoUrl())
                .repoName(repo.getRepoName())
                .primaryLanguage(repo.getPrimaryLanguage())
                .framework(repo.getFramework())
                .sizeKb(repo.getSizeKb())
                .dependencies(deps)
                .folderStructure(folderTree)
                .buildTool(repo.getBuildTool())
                .modules(deserializeList(repo.getModules()))
                .configurationFiles(deserializeList(repo.getConfigurationFiles()))
                .controllers(deserializeList(repo.getControllers()))
                .services(deserializeList(repo.getServices()))
                .repositories(deserializeList(repo.getRepositories()))
                .entities(deserializeList(repo.getEntities()))
                .utilities(deserializeList(repo.getUtilities()))
                .status(repo.getStatus().name())
                .createdAt(repo.getCreatedAt())
                .build();
    }
}
