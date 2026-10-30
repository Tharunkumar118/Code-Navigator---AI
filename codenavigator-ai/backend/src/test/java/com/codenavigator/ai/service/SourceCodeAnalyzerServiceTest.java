package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.ClassDetailsDto;
import com.codenavigator.ai.dto.InheritanceNodeDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.SourceCodeResult;
import com.codenavigator.ai.repository.SourceCodeResultRepository;
import com.codenavigator.ai.service.impl.SourceCodeAnalyzerServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class SourceCodeAnalyzerServiceTest {

    private SourceCodeAnalyzerService sourceCodeAnalyzerService;
    private SourceCodeResultRepository sourceCodeResultRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        sourceCodeResultRepository = Mockito.mock(SourceCodeResultRepository.class);
        sourceCodeAnalyzerService = new SourceCodeAnalyzerServiceImpl(sourceCodeResultRepository, objectMapper);
    }

    @Test
    void analyze_withValidJavaFiles_computesComplexityAndMetrics(@TempDir File tempDir) throws IOException {
        // 1. Create a mock Java package layout
        File sourceDir = new File(tempDir, "src/main/java/com/example/metrics");
        sourceDir.mkdirs();

        // Parent class
        File parentFile = new File(sourceDir, "Parent.java");
        Files.writeString(parentFile.toPath(), 
            "package com.example.metrics;\n" +
            "\n" +
            "public class Parent {\n" +
            "    protected String label;\n" +
            "    public void printLabel() {\n" +
            "        System.out.println(label);\n" +
            "    }\n" +
            "}"
        );

        // Child class with decision branching (if + loop)
        File childFile = new File(sourceDir, "Child.java");
        Files.writeString(childFile.toPath(), 
            "package com.example.metrics;\n" +
            "\n" +
            "import com.example.metrics.Parent;\n" +
            "\n" +
            "public class Child extends Parent {\n" +
            "    private int threshold;\n" +
            "    public void evaluate(int value) {\n" +
            "        if (value > threshold) {\n" +
            "            for (int i = 0; i < 5; i++) {\n" +
            "                System.out.println(i);\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}"
        );

        // Mock AnalysisRecord
        CodeRepository repo = new CodeRepository();
        repo.setRepoName("MetricsApp");

        AnalysisRecord record = new AnalysisRecord();
        record.setId(22L);
        record.setRepository(repo);

        // 2. Execute analysis
        sourceCodeAnalyzerService.analyze(record, tempDir);

        // 3. Capture saved result and assert metrics
        ArgumentCaptor<SourceCodeResult> captor = ArgumentCaptor.forClass(SourceCodeResult.class);
        verify(sourceCodeResultRepository).save(captor.capture());
        SourceCodeResult savedResult = captor.getValue();

        assertNotNull(savedResult);
        assertEquals(record, savedResult.getAnalysisRecord());
        assertEquals(2, savedResult.getClassesCount());
        assertEquals(0, savedResult.getInterfacesCount());
        assertEquals(2, savedResult.getMethodsCount());
        assertTrue(savedResult.getTotalLinesOfCode() > 0);

        // Parse detailed structures from JSON columns
        List<ClassDetailsDto> classDetails = objectMapper.readValue(savedResult.getClassDetails(), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, ClassDetailsDto.class));
        List<InheritanceNodeDto> inheritanceTree = objectMapper.readValue(savedResult.getInheritanceTree(), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, InheritanceNodeDto.class));

        assertNotNull(classDetails);
        assertEquals(2, classDetails.size());

        // Find child class
        ClassDetailsDto childDto = classDetails.stream()
                .filter(c -> c.getName().equals("Child"))
                .findFirst()
                .orElse(null);
        assertNotNull(childDto);
        assertEquals("Parent", childDto.getSuperClass());
        assertEquals(1, childDto.getFields().size());
        assertEquals(1, childDto.getMethods().size());

        // Complexity evaluation:
        // Parent.printLabel() method has no branching: complexity = 1
        // Child.evaluate() method has 1 'if' statement and 1 'for' statement: complexity = 1 (base) + 1 (if) + 1 (for) = 3
        // Total complexity across 2 methods = 4. Average complexity = 4 / 2 = 2.0.
        assertEquals(3, childDto.getMethods().get(0).getCyclomaticComplexity());
        assertEquals(2.0, savedResult.getAverageCyclomaticComplexity());

        // Inheritance Tree assertions
        assertNotNull(inheritanceTree);
        InheritanceNodeDto parentNode = inheritanceTree.stream()
                .filter(n -> n.getClassName().equals("com.example.metrics.Parent"))
                .findFirst()
                .orElse(null);
        assertNotNull(parentNode);
        assertTrue(parentNode.getSubClasses().contains("com.example.metrics.Child"));
    }
}
