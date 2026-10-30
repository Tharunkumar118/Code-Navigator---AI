package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.DatabaseEntityDto;
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
public class DatabaseAnalyzerServiceTest {

    @Autowired
    private DatabaseAnalyzerService databaseAnalyzerService;

    @Autowired
    private DatabaseResultRepository databaseResultRepository;

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
        // Create temp test directory
        tempRepoDir = Files.createTempDirectory("test-db-repo");

        // Write a mock Java Entity
        File javaDir = new File(tempRepoDir.toFile(), "src/main/java/com/test");
        javaDir.mkdirs();
        File userJava = new File(javaDir, "User.java");
        try (FileWriter writer = new FileWriter(userJava)) {
            writer.write("package com.test;\n\n" +
                    "import jakarta.persistence.*;\n\n" +
                    "@Entity\n" +
                    "@Table(name = \"users\")\n" +
                    "public class User {\n" +
                    "    @Id\n" +
                    "    private Long id;\n" +
                    "    @Column(name = \"username\", nullable = false)\n" +
                    "    private String username;\n" +
                    "    @ManyToOne\n" +
                    "    private Department department;\n" +
                    "}\n");
        }

        // Write a mock Python SQLAlchemy Model
        File modelsPy = new File(tempRepoDir.toFile(), "models.py");
        try (FileWriter writer = new FileWriter(modelsPy)) {
            writer.write("from flask_sqlalchemy import SQLAlchemy\n" +
                    "db = SQLAlchemy()\n\n" +
                    "class Book(db.Model):\n" +
                    "    __tablename__ = 'books'\n" +
                    "    id = db.Column(db.Integer, primary_key=True)\n" +
                    "    title = db.Column(db.String(100), nullable=False)\n" +
                    "    author_id = db.Column(db.Integer, db.ForeignKey('authors.id'))\n");
        }

        // Setup mock database references
        testUser = new User();
        testUser.setFullName("Test Architect");
        testUser.setEmail("db-test@example.com");
        testUser.setPassword("encoded_pass");
        testUser.setEnabled(true);
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
        testUser = userRepository.save(testUser);

        testRepo = new CodeRepository();
        testRepo.setRepoName("DB-Test-Repo");
        testRepo.setRepoUrl("https://github.com/test/db");
        testRepo.setClonePath(tempRepoDir.toAbsolutePath().toString());
        testRepo.setStatus(RepoStatus.COMPLETED);
        testRepo.setOwner(testUser);
        testRepo.setSizeKb(100L);
        testRepo = codeRepositoryRepository.save(testRepo);

        testRecord = new AnalysisRecord();
        testRecord.setRepository(testRepo);
        testRecord.setRequestedBy(testUser);
        testRecord.setType(AnalysisType.DATABASE);
        testRecord.setStatus(AnalysisStatus.RUNNING);
        testRecord = analysisRecordRepository.save(testRecord);
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Clean up mock database records
        databaseResultRepository.findByAnalysisRecordId(testRecord.getId())
                .ifPresent(r -> databaseResultRepository.delete(r));
        analysisRecordRepository.delete(testRecord);
        codeRepositoryRepository.delete(testRepo);
        userRepository.delete(testUser);

        // Delete temp directory files
        if (Files.exists(tempRepoDir)) {
            Files.walk(tempRepoDir)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }

    @Test
    public void testAnalyzeDatabaseSchema() throws Exception {
        // Trigger Database scan
        databaseAnalyzerService.analyze(testRecord, tempRepoDir.toFile());

        // Assert output result was saved
        DatabaseResult result = databaseResultRepository.findByAnalysisRecordId(testRecord.getId())
                .orElse(null);

        assertNotNull(result, "DatabaseResult should be created and saved");
        assertEquals(2, result.getEntitiesCount(), "Should detect two tables (User, Book)");
        assertEquals(2, result.getRelationshipsCount(), "Should detect 2 relationships (User->department, Book->author)");

        // Assert JSON details
        List<DatabaseEntityDto> list = objectMapper.readValue(result.getEntitiesJson(),
                new TypeReference<List<DatabaseEntityDto>>() {});
        assertEquals(2, list.size());

        DatabaseEntityDto userDto = list.stream().filter(e -> e.getClassName().equals("User")).findFirst().orElse(null);
        assertNotNull(userDto);
        assertEquals("users", userDto.getTableName());
        assertTrue(userDto.getPrimaryKeys().contains("id"));
        assertTrue(userDto.getForeignKeys().contains("department_id") || userDto.getForeignKeys().contains("department"));

        DatabaseEntityDto bookDto = list.stream().filter(e -> e.getClassName().equals("Book")).findFirst().orElse(null);
        assertNotNull(bookDto);
        assertEquals("books", bookDto.getTableName());
        assertTrue(bookDto.getPrimaryKeys().contains("id"));
        assertTrue(bookDto.getForeignKeys().contains("author_id"));

        // Assert Mermaid string structure
        String mermaid = result.getErDiagramMermaid();
        assertTrue(mermaid.contains("erDiagram"), "Should build valid Mermaid ER diagrams");
        assertTrue(mermaid.contains("USERS"), "Mermaid diagram should contain USERS table");
        assertTrue(mermaid.contains("BOOKS"), "Mermaid diagram should contain BOOKS table");

        // Assert Summary Markdown compilation
        String summary = result.getDatabaseSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("# Database Schema Summary"));
        assertTrue(summary.contains("Table: `users`"));
        assertTrue(summary.contains("Table: `books`"));
    }
}
