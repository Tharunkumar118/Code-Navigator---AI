package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.*;
import com.codenavigator.ai.repository.*;
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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DiagramGeneratorServiceTest {

    @Autowired
    private DiagramGeneratorService diagramGeneratorService;

    @Autowired
    private DiagramResultRepository diagramResultRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CodeRepositoryRepository codeRepositoryRepository;

    @Autowired
    private AnalysisRecordRepository analysisRecordRepository;

    private Path tempRepoDir;
    private CodeRepository testRepo;
    private AnalysisRecord testRecord;
    private User testUser;

    @BeforeEach
    public void setUp() throws Exception {
        tempRepoDir = Files.createTempDirectory("test-diagrams-repo");

        // Mock class
        File javaDir = new File(tempRepoDir.toFile(), "src/main/java/com/test");
        javaDir.mkdirs();
        File itemJava = new File(javaDir, "Item.java");
        try (FileWriter writer = new FileWriter(itemJava)) {
            writer.write("package com.test;\n\n" +
                    "public class Item {\n" +
                    "    private Long id;\n" +
                    "    private String name;\n" +
                    "    public void calculate() {}\n" +
                    "}\n");
        }

        // Setup mock db references
        testUser = new User();
        testUser.setFullName("Test Architect");
        testUser.setEmail("diagram-test@example.com");
        testUser.setPassword("encoded_pass");
        testUser.setEnabled(true);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);

        testRepo = new CodeRepository();
        testRepo.setRepoName("Diagram-Test-Repo");
        testRepo.setRepoUrl("https://github.com/test/diagrams");
        testRepo.setClonePath(tempRepoDir.toAbsolutePath().toString());
        testRepo.setStatus(RepoStatus.COMPLETED);
        testRepo.setOwner(testUser);
        testRepo.setSizeKb(100L);
        testRepo = codeRepositoryRepository.save(testRepo);

        testRecord = new AnalysisRecord();
        testRecord.setRepository(testRepo);
        testRecord.setRequestedBy(testUser);
        testRecord.setType(AnalysisType.ARCHITECTURE);
        testRecord.setStatus(AnalysisStatus.RUNNING);
        testRecord = analysisRecordRepository.save(testRecord);
    }

    @AfterEach
    public void tearDown() throws IOException {
        diagramResultRepository.findByAnalysisRecordId(testRecord.getId())
                .ifPresent(r -> diagramResultRepository.delete(r));
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
    public void testGenerateMermaidDiagrams() {
        diagramGeneratorService.generateDiagrams(testRecord, tempRepoDir.toFile());

        DiagramResult result = diagramResultRepository.findByAnalysisRecordId(testRecord.getId())
                .orElse(null);

        assertNotNull(result);
        assertNotNull(result.getClassDiagram());
        assertNotNull(result.getPackageDiagram());
        assertNotNull(result.getComponentDiagram());
        assertNotNull(result.getSequenceDiagram());
        assertNotNull(result.getUseCaseDiagram());
        assertNotNull(result.getArchitectureDiagram());

        assertTrue(result.getClassDiagram().contains("classDiagram"));
        assertTrue(result.getClassDiagram().contains("Item"));
        assertTrue(result.getPackageDiagram().contains("subgraph"));
        assertTrue(result.getSequenceDiagram().contains("sequenceDiagram"));
        assertTrue(result.getUseCaseDiagram().contains("graph"));
        assertTrue(result.getArchitectureDiagram().contains("graph"));
    }
}
