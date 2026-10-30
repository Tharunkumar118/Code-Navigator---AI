package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.AnalysisStatus;
import com.codenavigator.ai.repository.AnalysisRecordRepository;
import com.codenavigator.ai.service.AnalysisExecutor;
import com.codenavigator.ai.service.ArchitectureAnalyzerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisExecutorImpl implements AnalysisExecutor {

    private final AnalysisRecordRepository analysisRecordRepository;
    private final ArchitectureAnalyzerService architectureAnalyzerService;
    private final com.codenavigator.ai.service.SourceCodeAnalyzerService sourceCodeAnalyzerService;
    private final com.codenavigator.ai.service.ApiDocumentationGeneratorService apiDocumentationGeneratorService;
    private final com.codenavigator.ai.service.DatabaseAnalyzerService databaseAnalyzerService;
    private final com.codenavigator.ai.service.DiagramGeneratorService diagramGeneratorService;
    private final com.codenavigator.ai.service.CodeQualityAnalyzerService codeQualityAnalyzerService;
    private final com.codenavigator.ai.service.SecurityAnalyzerService securityAnalyzerService;

    @Override
    @Async
    @Transactional
    public void executeAnalysis(Long analysisRecordId) {
        log.info("Executing async analysis record ID: {}", analysisRecordId);
        
        AnalysisRecord record = null;
        for (int i = 0; i < 10; i++) {
            record = analysisRecordRepository.findById(analysisRecordId).orElse(null);
            if (record != null) {
                break;
            }
            log.debug("Analysis record ID {} not visible yet. Retrying in 100ms... (attempt {}/10)", analysisRecordId, i + 1);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Analysis execution interrupted while waiting for transaction visibility", e);
                return;
            }
        }

        if (record == null) {
            log.error("Analysis record ID {} not found after retries. Aborting.", analysisRecordId);
            return;
        }

        try {
            record.setStatus(AnalysisStatus.RUNNING);
            record = analysisRecordRepository.saveAndFlush(record);

            String clonePath = record.getRepository().getClonePath();
            if (clonePath == null || clonePath.isBlank()) {
                throw new IllegalStateException("Repository clone path is missing. Ensure repository is successfully cloned before running analysis.");
            }

            File repoDir = new File(clonePath);
            if (!repoDir.exists()) {
                throw new IllegalStateException("Repository directory not found at clone path: " + repoDir.getAbsolutePath());
            }

            switch (record.getType()) {
                case ARCHITECTURE:
                    architectureAnalyzerService.analyze(record, repoDir);
                    diagramGeneratorService.generateDiagrams(record, repoDir);
                    break;
                case SOURCE_CODE:
                    sourceCodeAnalyzerService.analyze(record, repoDir);
                    break;
                case API_DOCUMENTATION:
                    apiDocumentationGeneratorService.analyze(record, repoDir);
                    break;
                case DATABASE:
                    databaseAnalyzerService.analyze(record, repoDir);
                    break;
                case CODE_QUALITY:
                    codeQualityAnalyzerService.analyze(record, repoDir);
                    break;
                case SECURITY:
                    securityAnalyzerService.analyze(record, repoDir);
                    break;
                default:
                    throw new UnsupportedOperationException("Analysis type " + record.getType() + " is not yet supported.");
            }

            // Reload from DB because the analyzer might have updated it or saved results
            record = analysisRecordRepository.findById(analysisRecordId).orElse(record);
            record.setStatus(AnalysisStatus.COMPLETED);
            record.setCompletedAt(Instant.now());
            analysisRecordRepository.save(record);
            log.info("Analysis record ID {} completed successfully.", analysisRecordId);

        } catch (Exception e) {
            log.error("Analysis record ID {} failed during background execution. Error: {}", analysisRecordId, e.getMessage(), e);
            record.setStatus(AnalysisStatus.FAILED);
            record.setCompletedAt(Instant.now());
            analysisRecordRepository.save(record);
        }
    }
}
