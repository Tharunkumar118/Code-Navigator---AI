package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.ChatRequestDto;
import com.codenavigator.ai.dto.ChatResponseDto;
import com.codenavigator.ai.entity.*;
import com.codenavigator.ai.repository.*;
import com.codenavigator.ai.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final CodeRepositoryRepository repositoryRepository;
    private final AnalysisRecordRepository analysisRecordRepository;
    private final SourceCodeResultRepository sourceCodeResultRepository;
    private final ApiDocumentationResultRepository apiDocumentationResultRepository;
    private final DatabaseResultRepository databaseResultRepository;
    private final CodeQualityResultRepository codeQualityResultRepository;
    private final SecurityResultRepository securityResultRepository;

    // Bound from application.yml -> app.ai.gemini.*, which itself resolves from the
    // GEMINI_API_KEY / GEMINI_MODEL env vars. This replaces the old System.getenv()
    // lookup: Spring's @Value works consistently whether the value comes from an env
    // var, a JVM system property, application.yml, or a test property override -
    // System.getenv() alone only ever saw the first of those.
    @Value("${app.ai.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${app.ai.gemini.model:gemini-flash-latest}")
    private String geminiModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 10s connect / 30s read timeout - a hung network call must not hang the chat
    // request forever; better to fail fast and fall back to OFFLINE mode.
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }

    @Override
    public ChatResponseDto processChat(ChatRequestDto request, String email) {
        CodeRepository repo = repositoryRepository.findById(request.getRepositoryId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));

        // Compile structural summaries
        String metricsCtx = compileSourceMetricsContext(repo.getId());
        String apiCtx = compileApiContext(repo.getId());
        String dbCtx = compileDbContext(repo.getId());
        String qualityCtx = compileQualityContext(repo.getId());
        String securityCtx = compileSecurityContext(repo.getId());

        String systemPromptCtx = String.format(
                "You are CodeNavigator AI Navigator, a premium codebase architecture assistant. " +
                "You have parsed the codebase: %s (Language: %s, Build Tool: %s).\n\n" +
                "Here is the parsed codebase metadata context:\n" +
                "- Source Code Metrics: %s\n" +
                "- REST API Endpoints: %s\n" +
                "- Database Entities/Tables: %s\n" +
                "- Clean-Code Quality: %s\n" +
                "- Security Vulnerabilities Audit: %s\n\n" +
                "Answer the user's questions clearly, using markdown formatting.",
                repo.getRepoName(), repo.getPrimaryLanguage(), repo.getBuildTool(),
                metricsCtx, apiCtx, dbCtx, qualityCtx, securityCtx
        );

        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            try {
                return callLiveGeminiApi(systemPromptCtx, request.getPrompt(), geminiApiKey, geminiModel);
            } catch (RestClientResponseException e) {
                // Log the ACTUAL error Gemini sent back (bad model name, bad key, quota,
                // etc). Previously this detail was swallowed entirely, which is exactly
                // why the "always offline" symptom was invisible/undebuggable.
                log.error("Live Gemini API call failed with HTTP {} - response body: {}. Falling back to offline mode.",
                        e.getStatusCode().value(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Live Gemini API call failed, falling back to offline mode", e);
            }
        } else {
            log.warn("GEMINI_API_KEY is not configured - serving OFFLINE (rule-based) chat mode. " +
                    "Set the GEMINI_API_KEY environment variable to enable live AI responses.");
        }

        // Offline Rule Mode fallback
        return ChatResponseDto.builder()
                .message(compileOfflineResponse(request.getPrompt(), repo.getRepoName(), metricsCtx, apiCtx, dbCtx, qualityCtx, securityCtx))
                .mode("OFFLINE")
                .build();
    }

    private ChatResponseDto callLiveGeminiApi(String context, String prompt, String apiKey, String model) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Build Gemini request payload structure
        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", context + "\n\nUser Question: " + prompt);

        Map<String, Object> parts = new HashMap<>();
        parts.put("parts", Collections.singletonList(textPart));

        Map<String, Object> contents = new HashMap<>();
        contents.put("contents", Collections.singletonList(parts));

        String jsonPayload = objectMapper.writeValueAsString(contents);
        HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

        String rawResponse = restTemplate.postForObject(url, entity, String.class);
        Map<?, ?> map = objectMapper.readValue(rawResponse, Map.class);
        
        List<?> candidates = (List<?>) map.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new RuntimeException("No response candidates returned from Gemini API. Raw response: " + rawResponse);
        }
        
        Map<?, ?> candidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = (Map<?, ?>) candidate.get("content");
        List<?> partsList = (List<?>) content.get("parts");
        Map<?, ?> textPartResponse = (Map<?, ?>) partsList.get(0);
        String reply = (String) textPartResponse.get("text");

        return ChatResponseDto.builder()
                .message(reply)
                .mode("LIVE")
                .build();
    }

    private String compileOfflineResponse(String prompt, String repoName, String metrics, String apis, String db, String quality, String security) {
        String query = prompt.toLowerCase();

        if (query.contains("api") || query.contains("endpoint") || query.contains("route")) {
            return "### 📡 API Route Directory\n\n" + apis + 
                   "\n\n*Note: Setup a `GEMINI_API_KEY` system environment variable to ask complex conceptual queries!*";
        }
        if (query.contains("database") || query.contains("schema") || query.contains("table") || query.contains("er ")) {
            return "### 🗄️ Database Entity & Columns Catalog\n\n" + db + 
                   "\n\n*Note: Setup a `GEMINI_API_KEY` system environment variable to ask complex conceptual queries!*";
        }
        if (query.contains("quality") || query.contains("smell") || query.contains("score")) {
            return "### 🧹 Clean-Code Quality Audit\n\n" + quality + 
                   "\n\n*Note: Setup a `GEMINI_API_KEY` system environment variable to ask complex conceptual queries!*";
        }
        if (query.contains("security") || query.contains("vulnerabilit") || query.contains("sql injection")) {
            return "### 🛡️ Security Vulnerabilities catalog\n\n" + security + 
                   "\n\n*Note: Setup a `GEMINI_API_KEY` system environment variable to ask complex conceptual queries!*";
        }
        if (query.contains("metric") || query.contains("complexity") || query.contains("lines of code") || query.contains("loc")) {
            return "### 📊 Source Code Metrics\n\n" + metrics +
                   "\n\n*Note: Setup a `GEMINI_API_KEY` system environment variable to ask complex conceptual queries!*";
        }
        if (query.contains("help") || query.contains("command")) {
            return "### 💻 AI Navigator Commands\n\n" +
                   "- **api**: View all REST mappings and controller layers.\n" +
                   "- **database**: Inspect JPA tables and column parameters.\n" +
                   "- **quality**: Review cyclomatic violations and method size warnings.\n" +
                   "- **security**: Inspect vulnerability listings and resolution steps.\n" +
                   "- **metrics**: View class/method counts and complexity.\n\n" +
                   "*This is rule-based OFFLINE mode - it only recognizes the categories above. " +
                   "Set a `GEMINI_API_KEY` environment variable to ask any free-form question instead.*";
        }

        return String.format(
                "### 🤖 Codebase Navigator Summary: %s\n\n" +
                "I couldn't match your question to a specific category, so here's the full diagnostic summary instead:\n\n" +
                "1. **Metrics**: %s\n" +
                "2. **API Status**: %s\n" +
                "3. **Database Entities**: %s\n" +
                "4. **Quality Rating**: %s\n" +
                "5. **Security Audits**: %s\n\n" +
                "*This is rule-based OFFLINE mode, not a real AI model - it can only match a few fixed " +
                "keywords (api, database, quality, security, metrics, help). To ask genuinely free-form " +
                "questions and get real per-question answers, configure a `GEMINI_API_KEY` environment " +
                "variable and restart the backend. See the README for setup steps.*",
                repoName, metrics, apis, db, quality, security
        );
    }

    private String compileSourceMetricsContext(Long repoId) {
        return findLatestCompletedRecord(repoId, AnalysisType.SOURCE_CODE)
                .flatMap(rec -> sourceCodeResultRepository.findByAnalysisRecordId(rec.getId()))
                .map(res -> String.format("%d classes, %d methods, %d Lines of Code. Avg complexity: %.1f.",
                        res.getClassesCount(), res.getMethodsCount(), res.getTotalLinesOfCode(), res.getAverageCyclomaticComplexity()))
                .orElse("No metrics scan records found.");
    }

    private String compileApiContext(Long repoId) {
        return findLatestCompletedRecord(repoId, AnalysisType.API_DOCUMENTATION)
                .flatMap(rec -> apiDocumentationResultRepository.findByAnalysisRecordId(rec.getId()))
                .map(res -> {
                    if (res.getEndpointsJson() == null || res.getEndpointsJson().isEmpty()) return "0 endpoints mapped.";
                    return res.getEndpointsJson().length() > 600 ? res.getEndpointsJson().substring(0, 600) + "..." : res.getEndpointsJson();
                })
                .orElse("No API scan records found.");
    }

    private String compileDbContext(Long repoId) {
        return findLatestCompletedRecord(repoId, AnalysisType.DATABASE)
                .flatMap(rec -> databaseResultRepository.findByAnalysisRecordId(rec.getId()))
                .map(res -> {
                    if (res.getEntitiesJson() == null || res.getEntitiesJson().isEmpty()) return "0 entities mapped.";
                    return res.getEntitiesJson().length() > 600 ? res.getEntitiesJson().substring(0, 600) + "..." : res.getEntitiesJson();
                })
                .orElse("No Database scan records found.");
    }

    private String compileQualityContext(Long repoId) {
        return findLatestCompletedRecord(repoId, AnalysisType.CODE_QUALITY)
                .flatMap(rec -> codeQualityResultRepository.findByAnalysisRecordId(rec.getId()))
                .map(res -> String.format("Score: %d/100. Issues summary: %s",
                        res.getQualityScore(), res.getQualitySummary()))
                .orElse("No Quality scan records found.");
    }

    private String compileSecurityContext(Long repoId) {
        return findLatestCompletedRecord(repoId, AnalysisType.SECURITY)
                .flatMap(rec -> securityResultRepository.findByAnalysisRecordId(rec.getId()))
                .map(res -> String.format("Vulnerabilities: %d. Audit summary: %s",
                        res.getVulnerabilitiesCount(), res.getSecuritySummary()))
                .orElse("No Security scan records found.");
    }

    private Optional<AnalysisRecord> findLatestCompletedRecord(Long repoId, AnalysisType type) {
        return analysisRecordRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId).stream()
                .filter(rec -> rec.getType() == type && rec.getStatus() == AnalysisStatus.COMPLETED)
                .findFirst();
    }
}
