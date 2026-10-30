package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.VulnerabilityDto;
import com.codenavigator.ai.entity.*;
import com.codenavigator.ai.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SecurityAnalyzerServiceTest {

    @Autowired
    private SecurityAnalyzerService securityAnalyzerService;

    @Autowired
    private SecurityResultRepository securityResultRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CodeRepositoryRepository codeRepositoryRepository;

    @Autowired
    private AnalysisRecordRepository analysisRecordRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Path tempRepoDir;
    private CodeRepository testRepo;
    private AnalysisRecord testRecord;
    private User testUser;

    @BeforeEach
    public void setUp() throws Exception {
        tempRepoDir = Files.createTempDirectory("test-security-repo");

        // Write a mock Java class with deliberate SQL Injection concat
        File javaDir = new File(tempRepoDir.toFile(), "src/main/java/com/test");
        javaDir.mkdirs();
        File unsafeJava = new File(javaDir, "UnsafeClass.java");
        try (FileWriter writer = new FileWriter(unsafeJava)) {
            writer.write("package com.test;\n\n" +
                    "public class UnsafeClass {\n" +
                    "    public void runQuery(String input) {\n" +
                    "        String q = \"SELECT * FROM users WHERE name = '\" + input + \"'\";\n" +
                    "    }\n" +
                    "}\n");
        }

        // Write a mock properties file with hardcoded credentials and high JWT expiration
        File resourcesDir = new File(tempRepoDir.toFile(), "src/main/resources");
        resourcesDir.mkdirs();
        File appProps = new File(resourcesDir, "application.properties");
        try (FileWriter writer = new FileWriter(appProps)) {
            writer.write("spring.datasource.password=superSecretPassword123\n" +
                    "jwt.expiration=172800\n"); // 48 hours (limit is 24 hours)
        }

        // Setup database records
        testUser = new User();
        testUser.setFullName("Security Auditor");
        testUser.setEmail("security-test@example.com");
        testUser.setPassword("encoded_pass");
        testUser.setEnabled(true);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);

        testRepo = new CodeRepository();
        testRepo.setRepoName("Security-Test-Repo");
        testRepo.setRepoUrl("https://github.com/test/security");
        testRepo.setClonePath(tempRepoDir.toAbsolutePath().toString());
        testRepo.setStatus(RepoStatus.COMPLETED);
        testRepo.setOwner(testUser);
        testRepo.setSizeKb(100L);
        testRepo = codeRepositoryRepository.save(testRepo);

        testRecord = new AnalysisRecord();
        testRecord.setRepository(testRepo);
        testRecord.setRequestedBy(testUser);
        testRecord.setType(AnalysisType.SECURITY);
        testRecord.setStatus(AnalysisStatus.RUNNING);
        testRecord = analysisRecordRepository.save(testRecord);
    }

    @AfterEach
    public void tearDown() throws IOException {
        securityResultRepository.findByAnalysisRecordId(testRecord.getId())
                .ifPresent(r -> securityResultRepository.delete(r));
        analysisRecordRepository.delete(testRecord);
        codeRepositoryRepository.delete(testRepo);
        userRepository.delete(testUser);

        if (Files.exists(tempRepoDir)) {
            Files.walk(tempRepoDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testAnalyzeSecurity() throws Exception {
        securityAnalyzerService.analyze(testRecord, tempRepoDir.toFile());

        SecurityResult result = securityResultRepository.findByAnalysisRecordId(testRecord.getId())
                .orElse(null);

        assertNotNull(result);
        assertEquals(3, result.getVulnerabilitiesCount(), "Should detect exactly 3 security smells");

        List<VulnerabilityDto> vulns = objectMapper.readValue(result.getVulnerabilitiesJson(),
                new TypeReference<List<VulnerabilityDto>>() {});

        // Verify specific threat type detections
        boolean foundSqlInjection = vulns.stream().anyMatch(v -> v.getVulnType().contains("SQL Injection"));
        boolean foundHardcodedSecret = vulns.stream().anyMatch(v -> v.getVulnType().contains("Hardcoded"));
        boolean foundWeakJwt = vulns.stream().anyMatch(v -> v.getVulnType().contains("JWT"));

        assertTrue(foundSqlInjection, "Should find dynamic SQL query concatenation");
        assertTrue(foundHardcodedSecret, "Should flag raw spring datasource password");
        assertTrue(foundWeakJwt, "Should warning about JWT expiration exceeding 24 hours");
    }
}
