package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.*;
import com.codenavigator.ai.entity.*;
import com.codenavigator.ai.exception.ResourceNotFoundException;
import com.codenavigator.ai.repository.*;
import com.codenavigator.ai.service.AnalysisExecutor;
import com.codenavigator.ai.service.AnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisServiceImpl implements AnalysisService {

    private final UserRepository userRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final AnalysisRecordRepository analysisRecordRepository;
    private final ArchitectureResultRepository architectureResultRepository;
    private final SourceCodeResultRepository sourceCodeResultRepository;
    private final ApiDocumentationResultRepository apiDocumentationResultRepository;
    private final DatabaseResultRepository databaseResultRepository;
    private final DiagramResultRepository diagramResultRepository;
    private final CodeQualityResultRepository codeQualityResultRepository;
    private final SecurityResultRepository securityResultRepository;
    private final AnalysisExecutor analysisExecutor;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AnalysisResponse submitAnalysis(AnalysisRequest request, String userEmail) {
        log.info("User {} submitting analysis type {} for repository ID {}", userEmail, request.getType(), request.getRepositoryId());
        
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", userEmail));

        CodeRepository repo = codeRepositoryRepository.findById(request.getRepositoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", request.getRepositoryId()));

        // Tenant Security Check
        if (!repo.getOwner().getEmail().equals(userEmail)) {
            log.warn("Access denied: User {} tried to analyze repository ID {} owned by {}", userEmail, repo.getId(), repo.getOwner().getEmail());
            throw new AccessDeniedException("You do not have permission to analyze this repository");
        }

        // Validate repository is cloned successfully
        if (repo.getStatus() != RepoStatus.COMPLETED) {
            log.warn("Cannot analyze repository ID {} because it has status {}", repo.getId(), repo.getStatus());
            throw new IllegalStateException("Repository must be successfully cloned before triggering analysis (current status: " + repo.getStatus() + ")");
        }

        // Create AnalysisRecord
        AnalysisRecord record = new AnalysisRecord();
        record.setRepository(repo);
        record.setRequestedBy(user);
        record.setType(request.getType());
        record.setStatus(AnalysisStatus.QUEUED);

        final AnalysisRecord savedRecord = analysisRecordRepository.save(record);

        // Execute async background processing only after transaction commits successfully
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    analysisExecutor.executeAnalysis(savedRecord.getId());
                }
            });
        } else {
            analysisExecutor.executeAnalysis(savedRecord.getId());
        }

        return mapToResponse(savedRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisResponse getAnalysis(Long id, String userEmail) {
        log.debug("Fetching analysis record ID: {} requested by user: {}", id, userEmail);
        
        AnalysisRecord record = analysisRecordRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRecord", "id", id));

        // Tenant Security Check
        if (!record.getRequestedBy().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("You do not have permission to view this analysis result");
        }

        AnalysisResponse response = mapToResponse(record);

        // Attach results if completed
        if (record.getStatus() == AnalysisStatus.COMPLETED) {
            switch (record.getType()) {
                case ARCHITECTURE:
                    ArchitectureResult archResult = architectureResultRepository.findByAnalysisRecordId(record.getId()).orElse(null);
                    if (archResult != null) {
                        response.setResult(mapArchitectureResult(archResult));
                    }
                    break;
                case SOURCE_CODE:
                    com.codenavigator.ai.entity.SourceCodeResult sourceResult = sourceCodeResultRepository.findByAnalysisRecordId(record.getId()).orElse(null);
                    if (sourceResult != null) {
                        response.setResult(mapSourceCodeResult(sourceResult));
                    }
                    break;
                case API_DOCUMENTATION:
                    com.codenavigator.ai.entity.ApiDocumentationResult apiResult = apiDocumentationResultRepository.findByAnalysisRecordId(record.getId()).orElse(null);
                    if (apiResult != null) {
                        response.setResult(mapApiDocumentationResult(apiResult));
                    }
                    break;
                case DATABASE:
                    com.codenavigator.ai.entity.DatabaseResult dbResult = databaseResultRepository.findByAnalysisRecordId(record.getId()).orElse(null);
                    if (dbResult != null) {
                        response.setResult(mapDatabaseResult(dbResult));
                    }
                    break;
                case CODE_QUALITY:
                    com.codenavigator.ai.entity.CodeQualityResult qualityResult = codeQualityResultRepository.findByAnalysisRecordId(record.getId()).orElse(null);
                    if (qualityResult != null) {
                        response.setResult(mapCodeQualityResult(qualityResult));
                    }
                    break;
                case SECURITY:
                    com.codenavigator.ai.entity.SecurityResult securityResult = securityResultRepository.findByAnalysisRecordId(record.getId()).orElse(null);
                    if (securityResult != null) {
                        response.setResult(mapSecurityResult(securityResult));
                    }
                    break;
                default:
                    // Other results map in subsequent modules
                    break;
            }
        }

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisResponse> getAnalysisHistory(Long repositoryId, String userEmail) {
        log.debug("Fetching analysis history for repository ID: {} requested by user: {}", repositoryId, userEmail);
        
        CodeRepository repo = codeRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository", "id", repositoryId));

        if (!repo.getOwner().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("You do not have permission to view history for this repository");
        }

        List<AnalysisRecord> history = analysisRecordRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);

        return history.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private AnalysisResponse mapToResponse(AnalysisRecord record) {
        return AnalysisResponse.builder()
                .id(record.getId())
                .repositoryId(record.getRepository().getId())
                .repositoryName(record.getRepository().getRepoName())
                .type(record.getType().name())
                .status(record.getStatus().name())
                .createdAt(record.getCreatedAt())
                .completedAt(record.getCompletedAt())
                .build();
    }

    private ArchitectureResultDto mapArchitectureResult(ArchitectureResult result) {
        FolderNodeDto packageTree = null;
        if (result.getPackageStructure() != null) {
            try {
                packageTree = objectMapper.readValue(result.getPackageStructure(), FolderNodeDto.class);
            } catch (Exception e) {
                log.error("Failed to map packageStructure JSON", e);
            }
        }
        
        GraphDto classDeps = null;
        if (result.getDependencyGraph() != null) {
            try {
                classDeps = objectMapper.readValue(result.getDependencyGraph(), GraphDto.class);
            } catch (Exception e) {
                log.error("Failed to map dependencyGraph JSON", e);
            }
        }

        GraphDto moduleDeps = null;
        if (result.getModuleGraph() != null) {
            try {
                moduleDeps = objectMapper.readValue(result.getModuleGraph(), GraphDto.class);
            } catch (Exception e) {
                log.error("Failed to map moduleGraph JSON", e);
            }
        }

        GraphDto callGraph = null;
        if (result.getCallGraph() != null) {
            try {
                callGraph = objectMapper.readValue(result.getCallGraph(), GraphDto.class);
            } catch (Exception e) {
                log.error("Failed to map callGraph JSON", e);
            }
        }

        DiagramResult diagrams = diagramResultRepository.findByAnalysisRecordId(result.getAnalysisRecord().getId()).orElse(null);
        DiagramResultDto diagramsDto = null;
        if (diagrams != null) {
            diagramsDto = DiagramResultDto.builder()
                    .classDiagram(diagrams.getClassDiagram())
                    .packageDiagram(diagrams.getPackageDiagram())
                    .componentDiagram(diagrams.getComponentDiagram())
                    .sequenceDiagram(diagrams.getSequenceDiagram())
                    .useCaseDiagram(diagrams.getUseCaseDiagram())
                    .architectureDiagram(diagrams.getArchitectureDiagram())
                    .build();
        }

        return ArchitectureResultDto.builder()
                .projectStructure(result.getProjectStructure())
                .packageStructure(packageTree)
                .dependencyGraph(classDeps)
                .moduleGraph(moduleDeps)
                .callGraph(callGraph)
                .architectureSummary(result.getArchitectureSummary())
                .diagrams(diagramsDto)
                .build();
    }

    private SourceCodeResultDto mapSourceCodeResult(com.codenavigator.ai.entity.SourceCodeResult result) {
        if (result == null) return null;

        List<ClassDetailsDto> classDetails = null;
        if (result.getClassDetails() != null) {
            try {
                classDetails = objectMapper.readValue(result.getClassDetails(), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<ClassDetailsDto>>() {});
            } catch (Exception e) {
                log.error("Failed to map classDetails JSON", e);
            }
        }

        List<InheritanceNodeDto> inheritanceTree = null;
        if (result.getInheritanceTree() != null) {
            try {
                inheritanceTree = objectMapper.readValue(result.getInheritanceTree(), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<InheritanceNodeDto>>() {});
            } catch (Exception e) {
                log.error("Failed to map inheritanceTree JSON", e);
            }
        }

        return SourceCodeResultDto.builder()
                .classesCount(result.getClassesCount())
                .interfacesCount(result.getInterfacesCount())
                .methodsCount(result.getMethodsCount())
                .totalLinesOfCode(result.getTotalLinesOfCode())
                .averageCyclomaticComplexity(result.getAverageCyclomaticComplexity())
                .classDetails(classDetails != null ? classDetails : new ArrayList<>())
                .inheritanceTree(inheritanceTree != null ? inheritanceTree : new ArrayList<>())
                .build();
    }

    private ApiDocumentationResultDto mapApiDocumentationResult(com.codenavigator.ai.entity.ApiDocumentationResult result) {
        if (result == null) return null;

        List<EndpointDetailsDto> endpoints = null;
        if (result.getEndpointsJson() != null) {
            try {
                endpoints = objectMapper.readValue(result.getEndpointsJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<EndpointDetailsDto>>() {});
            } catch (Exception e) {
                log.error("Failed to map endpointsJson JSON", e);
            }
        }

        Map<String, Object> openapiDoc = null;
        if (result.getOpenapiDoc() != null) {
            try {
                openapiDoc = objectMapper.readValue(result.getOpenapiDoc(), 
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("Failed to map openapiDoc JSON", e);
            }
        }

        return ApiDocumentationResultDto.builder()
                .endpointsCount(result.getEndpointsCount())
                .endpoints(endpoints != null ? endpoints : new ArrayList<>())
                .openapiDoc(openapiDoc != null ? openapiDoc : new HashMap<>())
                .build();
    }

    private DatabaseResultDto mapDatabaseResult(com.codenavigator.ai.entity.DatabaseResult result) {
        if (result == null) return null;

        List<DatabaseEntityDto> entities = null;
        if (result.getEntitiesJson() != null) {
            try {
                entities = objectMapper.readValue(result.getEntitiesJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<DatabaseEntityDto>>() {});
            } catch (Exception e) {
                log.error("Failed to map entitiesJson JSON", e);
            }
        }

        return DatabaseResultDto.builder()
                .entitiesCount(result.getEntitiesCount())
                .relationshipsCount(result.getRelationshipsCount())
                .entities(entities != null ? entities : new ArrayList<>())
                .erDiagramMermaid(result.getErDiagramMermaid())
                .databaseSummary(result.getDatabaseSummary())
                .build();
    }

    private CodeQualityResultDto mapCodeQualityResult(com.codenavigator.ai.entity.CodeQualityResult result) {
        if (result == null) return null;

        List<QualityIssueDto> issues = null;
        if (result.getIssuesJson() != null) {
            try {
                issues = objectMapper.readValue(result.getIssuesJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<QualityIssueDto>>() {});
            } catch (Exception e) {
                log.error("Failed to map issuesJson JSON", e);
            }
        }

        return CodeQualityResultDto.builder()
                .qualityScore(result.getQualityScore())
                .issues(issues != null ? issues : new ArrayList<>())
                .qualitySummary(result.getQualitySummary())
                .build();
    }

    private SecurityResultDto mapSecurityResult(com.codenavigator.ai.entity.SecurityResult result) {
        if (result == null) return null;

        List<VulnerabilityDto> vulnerabilities = null;
        if (result.getVulnerabilitiesJson() != null) {
            try {
                vulnerabilities = objectMapper.readValue(result.getVulnerabilitiesJson(), 
                        new com.fasterxml.jackson.core.type.TypeReference<List<VulnerabilityDto>>() {});
            } catch (Exception e) {
                log.error("Failed to map vulnerabilitiesJson JSON", e);
            }
        }

        return SecurityResultDto.builder()
                .vulnerabilitiesCount(result.getVulnerabilitiesCount())
                .vulnerabilities(vulnerabilities != null ? vulnerabilities : new ArrayList<>())
                .securitySummary(result.getSecuritySummary())
                .build();
    }
}
