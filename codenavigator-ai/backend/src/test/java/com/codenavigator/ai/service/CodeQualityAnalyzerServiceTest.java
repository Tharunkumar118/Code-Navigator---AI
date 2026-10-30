package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.QualityIssueDto;
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
public class CodeQualityAnalyzerServiceTest {

    @Autowired
    private CodeQualityAnalyzerService codeQualityAnalyzerService;

    @Autowired
    private CodeQualityResultRepository codeQualityResultRepository;

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
        tempRepoDir = Files.createTempDirectory("test-quality-repo");

        // Write a mock Java class with deliberate violations
        File javaDir = new File(tempRepoDir.toFile(), "src/main/java/com/test");
        javaDir.mkdirs();
        File badClassJava = new File(javaDir, "badClass.java");
        try (FileWriter writer = new FileWriter(badClassJava)) {
            // Violations:
            // 1. Lowercase class name: badClass
            // 2. Uppercase field: String Username
            // 3. Unused local variable: int unusedVal = 5;
            // 4. Long Method (35 lines)
            writer.write("package com.test;\n\n" +
                    "public class badClass {\n" +
                    "    private String Username;\n\n" +
                    "    public void complexAndLongMethod(int x) {\n" +
                    "        int unusedVal = 5;\n" +
                    "        System.out.println(Username);\n" +
                    "        // Line 1\n" +
                    "        // Line 2\n" +
                    "        // Line 3\n" +
                    "        // Line 4\n" +
                    "        // Line 5\n" +
                    "        // Line 6\n" +
                    "        // Line 7\n" +
                    "        // Line 8\n" +
                    "        // Line 9\n" +
                    "        // Line 10\n" +
                    "        // Line 11\n" +
                    "        // Line 12\n" +
                    "        // Line 13\n" +
                    "        // Line 14\n" +
                    "        // Line 15\n" +
                    "        // Line 16\n" +
                    "        // Line 17\n" +
                    "        // Line 18\n" +
                    "        // Line 19\n" +
                    "        // Line 20\n" +
                    "        // Line 21\n" +
                    "        // Line 22\n" +
                    "        // Line 23\n" +
                    "        // Line 24\n" +
                    "        // Line 25\n" +
                    "        // Line 26\n" +
                    "        // Line 27\n" +
                    "        // Line 28\n" +
                    "        // Line 29\n" +
                    "        // Line 30\n" +
                    "    }\n" +
                    "}\n");
        }

        // Setup database records
        testUser = new User();
        testUser.setFullName("Test Architect");
        testUser.setEmail("quality-test@example.com");
        testUser.setPassword("encoded_pass");
        testUser.setEnabled(true);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);

        testRepo = new CodeRepository();
        testRepo.setRepoName("Quality-Test-Repo");
        testRepo.setRepoUrl("https://github.com/test/quality");
        testRepo.setClonePath(tempRepoDir.toAbsolutePath().toString());
        testRepo.setStatus(RepoStatus.COMPLETED);
        testRepo.setOwner(testUser);
        testRepo.setSizeKb(100L);
        testRepo = codeRepositoryRepository.save(testRepo);

        testRecord = new AnalysisRecord();
        testRecord.setRepository(testRepo);
        testRecord.setRequestedBy(testUser);
        testRecord.setType(AnalysisType.CODE_QUALITY);
        testRecord.setStatus(AnalysisStatus.RUNNING);
        testRecord = analysisRecordRepository.save(testRecord);
    }

    @AfterEach
    public void tearDown() throws IOException {
        codeQualityResultRepository.findByAnalysisRecordId(testRecord.getId())
                .ifPresent(r -> codeQualityResultRepository.delete(r));
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
    public void testAnalyzeCodeQuality() throws Exception {
        codeQualityAnalyzerService.analyze(testRecord, tempRepoDir.toFile());

        CodeQualityResult result = codeQualityResultRepository.findByAnalysisRecordId(testRecord.getId())
                .orElse(null);

        assertNotNull(result);
        assertTrue(result.getQualityScore() < 100, "Quality score should deduct points for violations");

        List<QualityIssueDto> issuesList = objectMapper.readValue(result.getIssuesJson(),
                new TypeReference<List<QualityIssueDto>>() {});

        assertTrue(issuesList.size() >= 3, "Should detect at least 3 code smells in mock java class");

        // Verify specific issue type detections
        boolean foundClassNameSmell = issuesList.stream().anyMatch(i -> i.getDescription().contains("badClass"));
        boolean foundFieldNameSmell = issuesList.stream().anyMatch(i -> i.getDescription().contains("Username"));
        boolean foundUnusedVarSmell = issuesList.stream().anyMatch(i -> i.getDescription().contains("unusedVal"));
        boolean foundLongMethodSmell = issuesList.stream().anyMatch(i -> i.getRuleName().contains("Long Method"));

        assertTrue(foundClassNameSmell, "Should find PascalCase class violation");
        assertTrue(foundFieldNameSmell, "Should find camelCase variable violation");
        assertTrue(foundUnusedVarSmell, "Should find unused local variable warning");
        assertTrue(foundLongMethodSmell, "Should detect method length > 30 lines smell");
    }
}
