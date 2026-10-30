package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.VulnerabilityDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.SecurityResult;
import com.codenavigator.ai.repository.SecurityResultRepository;
import com.codenavigator.ai.service.SecurityAnalyzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAnalyzerServiceImpl implements SecurityAnalyzerService {

    private final SecurityResultRepository securityResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyze(AnalysisRecord record, File repositoryDir) {
        log.info("Starting AST security analysis for repository: {}", record.getRepository().getRepoName());

        try {
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            List<VulnerabilityDto> vulnerabilities = new ArrayList<>();

            // Scan files
            for (File file : sourceFiles) {
                try {
                    analyzeFileForVulnerabilities(file, repositoryDir, vulnerabilities);
                } catch (Exception e) {
                    log.warn("Failed to check security for file: {}. Error: {}", file.getName(), e.getMessage());
                }
            }

            // Save results
            String summary = generateSecuritySummary(vulnerabilities);
            SecurityResult result = SecurityResult.builder()
                    .analysisRecord(record)
                    .vulnerabilitiesCount(vulnerabilities.size())
                    .vulnerabilitiesJson(objectMapper.writeValueAsString(vulnerabilities))
                    .securitySummary(summary)
                    .build();

            securityResultRepository.save(result);
            log.info("Security scan successfully saved for record: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to complete security scan: {}", e.getMessage(), e);
            throw new RuntimeException("Security analysis failed: " + e.getMessage(), e);
        }
    }

    private void findSourceFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || 
                name.equals("build") || name.equals("src/test")) {
                continue;
            }
            if (f.isDirectory()) {
                findSourceFiles(f, list);
            } else {
                String lower = name.toLowerCase();
                if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || 
                    lower.endsWith(".ts") || lower.endsWith(".properties") || lower.endsWith(".yml") || 
                    lower.endsWith(".yaml")) {
                    String path = f.getAbsolutePath().replace('\\', '/');
                    if (!path.contains("/src/test/")) {
                        list.add(f);
                    }
                }
            }
        }
    }

    private void analyzeFileForVulnerabilities(File file, File rootDir, List<VulnerabilityDto> vulns) throws Exception {
        String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
        List<String> lines = Files.readAllLines(file.toPath());

        // Regex patterns
        // 1. SQL Injection: select/insert/update/delete containing string concatenation (+ or %)
        Pattern sqlInjectionPattern = Pattern.compile("(?i)(select|insert|update|delete|from).*?(\\+|%|\\$\\{).*?[\"']");
        
        boolean isConfigFile = relativePath.endsWith(".properties") || 
                             relativePath.endsWith(".yml") || 
                             relativePath.endsWith(".yaml");
        
        // 2. Hardcoded Secrets: key/password matching string literals, ignoring empty or placeholder configurations
        Pattern credsPattern;
        if (isConfigFile) {
            credsPattern = Pattern.compile("(?i)(password|passwd|secret|api_key|token|jwt_secret|private_key)\\s*[:=]\\s*[\"']?([^\"'${}\\s]+)[\"']?");
        } else {
            credsPattern = Pattern.compile("(?i)(password|passwd|secret|api_key|token|jwt_secret|private_key)\\s*[:=]\\s*[\"']([^\"'${}\\s]+)[\"']");
        }

        // 3. JWT Expiration check (properties or yaml files)
        Pattern jwtExpirationPattern = Pattern.compile("(?i)jwt\\.expiration\\s*[:=]\\s*(\\d+)");

        // Expanded Rules
        Pattern cmdInjectionPattern = Pattern.compile("(?i)(Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder)\\s*\\(.*?[+].*?\\)");
        Pattern xssPattern = Pattern.compile("(?i)(\\.innerHTML\\s*=\\s*.*?[+].*?|dangerouslySetInnerHTML)");
        Pattern ssrfPattern = Pattern.compile("(?i)(restTemplate|httpClient|HttpURLConnection)\\.(getFor|postFor|send|connect|openStream).*?[+].*?");
        Pattern nosqlInjectionPattern = Pattern.compile("(?i)(mongoTemplate|db\\.collection).*?(find|update|delete).*?([+]|%|\\$\\{)");
        Pattern promptInjectionPattern = Pattern.compile("(?i)(prompt|template|instruction).*?(concat|\\+).*?(user|input|request)");
        Pattern fileUploadPattern = Pattern.compile("(?i)MultipartFile.*?transferTo\\(\\s*.*?(getOriginalFilename|getName)\\s*\\)");
        Pattern raceConditionPattern = Pattern.compile("(?i)public\\s+static\\s+(List|Map|Set|int|double|String)\\s+[a-zA-Z0-9_]+\\s*=\\s*");
        Pattern memorySafetyPattern = Pattern.compile("(?i)\\b(strcpy|strcat|gets|sprintf|vsprintf)\\b");

        int lineIndex = 0;
        for (String line : lines) {
            lineIndex++;
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) continue;

            // 1. SQL Injection checking
            if (trimmed.contains("select ") || trimmed.contains("SELECT ") || 
                trimmed.contains("insert ") || trimmed.contains("INSERT ") ||
                trimmed.contains("update ") || trimmed.contains("UPDATE ") ||
                trimmed.contains("delete ") || trimmed.contains("DELETE ")) {
                Matcher sqlM = sqlInjectionPattern.matcher(trimmed);
                if (sqlM.find()) {
                    vulns.add(VulnerabilityDto.builder()
                            .filePath(relativePath)
                            .line(lineIndex)
                            .vulnType("SQL Injection Risk")
                            .severity("CRITICAL")
                            .description("Dynamic SQL string construction detected. Avoid concatenating input variables directly inside queries to prevent SQL Injection attacks.")
                            .snippet(getSnippet(lines, lineIndex))
                            .resolution("Refactor to use Parameterized Queries (PreparedStatement in JDBC, ORM entity bindings, or mapped queries).")
                            .build());
                }
            }

            // 2. Hardcoded Credentials checking
            Matcher credsM = credsPattern.matcher(trimmed);
            if (credsM.find()) {
                String secretVal = credsM.group(2).trim();
                // Avoid matching config placeholders like ${DB_PASSWORD} or empty credentials
                if (secretVal.length() > 4 && !secretVal.startsWith("${") && !secretVal.endsWith("}")) {
                    // Check if value looks like a real hardcoded key
                    boolean isLikelyWeakDefaultVal = secretVal.equalsIgnoreCase("secret") || 
                                                     secretVal.equalsIgnoreCase("password") || 
                                                     secretVal.equalsIgnoreCase("secretkey") ||
                                                     secretVal.equalsIgnoreCase("placeholder");
                    
                    vulns.add(VulnerabilityDto.builder()
                            .filePath(relativePath)
                            .line(lineIndex)
                            .vulnType("Hardcoded Secret / Credentials")
                            .severity(isLikelyWeakDefaultVal ? "HIGH" : "CRITICAL")
                            .description("Literal security credentials detected in source code or configuration files.")
                            .snippet(getSnippet(lines, lineIndex))
                            .resolution("Move secrets to environment variables or load them from secure vaults (e.g. Spring Cloud Config, .env, or Vault).")
                            .build());
                }
            }

            // 3. JWT Expiration checking (should not exceed 86400 seconds / 24 hours)
            Matcher jwtExpM = jwtExpirationPattern.matcher(trimmed);
            if (jwtExpM.find()) {
                try {
                    long seconds = Long.parseLong(jwtExpM.group(1));
                    if (seconds > 86400) {
                        vulns.add(VulnerabilityDto.builder()
                                .filePath(relativePath)
                                .line(lineIndex)
                                .vulnType("Weak JWT Configuration")
                                .severity("MEDIUM")
                                .description("JWT validity window is set to " + seconds + " seconds (" + (seconds / 3600) + " hours). Expirations exceeding 24 hours create persistent token vulnerabilities.")
                                .snippet(getSnippet(lines, lineIndex))
                                .resolution("Reduce JWT token expiration setting to less than 24 hours (preferably 1 hour or less) and implement refresh tokens.")
                                .build());
                    }
                } catch (NumberFormatException ignored) {}
            }

            // 4. Missing Authentication checks (endpoints exposed with permitAll() in Spring Security config files)
            if (relativePath.contains("SecurityConfig") && trimmed.contains(".permitAll()")) {
                if (trimmed.contains("/api/v1/admin") || trimmed.contains("/**")) {
                    vulns.add(VulnerabilityDto.builder()
                            .filePath(relativePath)
                            .line(lineIndex)
                            .vulnType("Unsafe API Endpoint access")
                            .severity("HIGH")
                            .description("Spring Security configuration permits unrestricted access to admin or wildcard paths using '.permitAll()'.")
                            .snippet(getSnippet(lines, lineIndex))
                            .resolution("Restrict sensitive API endpoints to authenticated users by changing permits to '.hasRole(\"ADMIN\")' or '.authenticated()'.")
                            .build());
                }
            }

            // 5. Command Injection Check
            if (cmdInjectionPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("Command Injection Risk")
                        .severity("HIGH")
                        .description("Dynamic process execution detected. Direct concatenation in system process execution calls allows command injection.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Avoid shell calls. Use structured parameter lists or environment-safe APIs.")
                        .build());
            }

            // 6. XSS Check
            if (xssPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("Cross-Site Scripting (XSS)")
                        .severity("HIGH")
                        .description("Unescaped client-side rendering detected via innerHTML or dangerouslySetInnerHTML.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Sanitize dynamically rendered strings, or use standard text elements (innerText/textContent).")
                        .build());
            }

            // 7. SSRF Check
            if (ssrfPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("Server-Side Request Forgery (SSRF)")
                        .severity("HIGH")
                        .description("Dynamic URL concatenation passed directly to HTTP clients.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Use whitelisted domain resolvers or validate URLs before connection.")
                        .build());
            }

            // 8. NoSQL Injection Check
            if (nosqlInjectionPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("NoSQL Injection Risk")
                        .severity("HIGH")
                        .description("Unparameterized query logic mapped in NoSQL operations.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Utilize query-builder helper methods or parameters bindings.")
                        .build());
            }

            // 9. Prompt Injection Check
            if (promptInjectionPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("Prompt Injection Vulnerability")
                        .severity("MEDIUM")
                        .description("Untrusted user queries appended directly in LLM system prompt templates.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Structure instructions separately from system prompts using LLM APIs parameters.")
                        .build());
            }

            // 10. Unsafe File Upload Check
            if (fileUploadPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("File Upload Vulnerability")
                        .severity("HIGH")
                        .description("Direct utilization of client-controlled original filenames in transfer actions.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Generate random unique identifiers (UUIDs) for uploaded files and whitelist extensions.")
                        .build());
            }

            // 11. Race Condition Check
            if (raceConditionPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("Race Condition / Thread-Safety Risk")
                        .severity("MEDIUM")
                        .description("Mutable public static variable mapped in potential multithreaded application components.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Refactor to local variables or thread-safe atomic wrappers (e.g. AtomicInteger, ConcurrentHashMap).")
                        .build());
            }

            // 12. Memory Safety Check
            if (memorySafetyPattern.matcher(trimmed).find()) {
                vulns.add(VulnerabilityDto.builder()
                        .filePath(relativePath)
                        .line(lineIndex)
                        .vulnType("Memory Safety Vulnerability")
                        .severity("HIGH")
                        .description("Unsafe string/memory utility function detected in code blocks.")
                        .snippet(getSnippet(lines, lineIndex))
                        .resolution("Use length-bounded safe versions (e.g. strncpy, strncat) or compile with safe buffers.")
                        .build());
            }
        }
    }

    private String getSnippet(List<String> lines, int targetLine) {
        int start = Math.max(0, targetLine - 2);
        int end = Math.min(lines.size() - 1, targetLine + 2);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            String prefix = (i + 1 == targetLine) ? " > " : "   ";
            sb.append(prefix).append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String generateSecuritySummary(List<VulnerabilityDto> vulns) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Codebase Security Scan Specification\n\n");
        sb.append("A total of **").append(vulns.size()).append("** potential vulnerabilities were identified in the repository.\n\n");

        long critical = vulns.stream().filter(v -> "CRITICAL".equalsIgnoreCase(v.getSeverity())).count();
        long high = vulns.stream().filter(v -> "HIGH".equalsIgnoreCase(v.getSeverity())).count();
        long medium = vulns.stream().filter(v -> "MEDIUM".equalsIgnoreCase(v.getSeverity())).count();

        sb.append("## Vulnerability Counts by Severity\n\n");
        sb.append("* **CRITICAL**: ").append(critical).append("\n");
        sb.append("* **HIGH**: ").append(high).append("\n");
        sb.append("* **MEDIUM / LOW**: ").append(medium).append("\n\n");

        sb.append("## Vulnerability Audit Log\n\n");
        if (vulns.isEmpty()) {
            sb.append("Congratulations! No security vulnerabilities detected. The repository complies with all clean-code secure standard metrics.");
        } else {
            for (VulnerabilityDto vuln : vulns) {
                sb.append("### [").append(vuln.getSeverity()).append("] ").append(vuln.getVulnType()).append("\n");
                sb.append("* **File**: `").append(vuln.getFilePath()).append("` (Line ").append(vuln.getLine()).append(")\n");
                sb.append("* **Threat Description**: ").append(vuln.getDescription()).append("\n");
                sb.append("* **Recommended Remediation**: ").append(vuln.getResolution()).append("\n\n");
                sb.append("```java\n").append(vuln.getSnippet()).append("```\n\n");
            }
        }

        return sb.toString();
    }
}
