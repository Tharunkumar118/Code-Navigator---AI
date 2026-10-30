package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.FolderNodeDto;
import com.codenavigator.ai.dto.GraphDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.ArchitectureResult;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.repository.ArchitectureResultRepository;
import com.codenavigator.ai.service.impl.ArchitectureAnalyzerServiceImpl;
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

class ArchitectureAnalyzerServiceTest {

    private ArchitectureAnalyzerService architectureAnalyzerService;
    private ArchitectureResultRepository architectureResultRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        architectureResultRepository = Mockito.mock(ArchitectureResultRepository.class);
        architectureAnalyzerService = new ArchitectureAnalyzerServiceImpl(architectureResultRepository, objectMapper);
    }

    @Test
    void analyze_withValidJavaFiles_buildsDependenciesAndCallGraphs(@TempDir File tempDir) throws IOException {
        // 1. Create a dummy Java layered project
        File sourceDir = new File(tempDir, "src/main/java/com/example/demo");
        sourceDir.mkdirs();

        // Service class
        File serviceFile = new File(sourceDir, "MyService.java");
        Files.writeString(serviceFile.toPath(), 
            "package com.example.demo;\n" +
            "\n" +
            "public class MyService {\n" +
            "    public void doWork() {\n" +
            "        // work done\n" +
            "    }\n" +
            "}"
        );

        // Controller class (imports Service, invokes method)
        File controllerFile = new File(sourceDir, "MyController.java");
        Files.writeString(controllerFile.toPath(), 
            "package com.example.demo;\n" +
            "\n" +
            "import com.example.demo.MyService;\n" +
            "\n" +
            "public class MyController {\n" +
            "    private MyService service;\n" +
            "    public void handleRequest() {\n" +
            "        service.doWork();\n" +
            "    }\n" +
            "}"
        );

        // Mock AnalysisRecord
        CodeRepository repo = new CodeRepository();
        repo.setRepoName("TestJavaApp");
        repo.setPrimaryLanguage("Java");
        repo.setFramework("Spring Boot");

        AnalysisRecord record = new AnalysisRecord();
        record.setId(10L);
        record.setRepository(repo);

        // 2. Execute analysis
        architectureAnalyzerService.analyze(record, tempDir);

        // 3. Capture saved result and assert structures
        ArgumentCaptor<ArchitectureResult> captor = ArgumentCaptor.forClass(ArchitectureResult.class);
        verify(architectureResultRepository).save(captor.capture());
        ArchitectureResult savedResult = captor.getValue();

        assertNotNull(savedResult);
        assertEquals(record, savedResult.getAnalysisRecord());
        assertTrue(savedResult.getProjectStructure().contains("TestJavaApp"));

        // Deserialize graphs
        GraphDto classDeps = objectMapper.readValue(savedResult.getDependencyGraph(), GraphDto.class);
        GraphDto callGraph = objectMapper.readValue(savedResult.getCallGraph(), GraphDto.class);
        FolderNodeDto packageTree = objectMapper.readValue(savedResult.getPackageStructure(), FolderNodeDto.class);

        // Class Dependency Assertions
        // Expect both classes as nodes
        boolean hasControllerNode = classDeps.getNodes().stream().anyMatch(n -> n.getId().equals("com.example.demo.MyController"));
        boolean hasServiceNode = classDeps.getNodes().stream().anyMatch(n -> n.getId().equals("com.example.demo.MyService"));
        assertTrue(hasControllerNode);
        assertTrue(hasServiceNode);

        // Expect edge: MyController -> MyService
        boolean hasDependencyEdge = classDeps.getEdges().stream().anyMatch(e -> 
            e.getSource().equals("com.example.demo.MyController") && e.getTarget().equals("com.example.demo.MyService"));
        assertTrue(hasDependencyEdge);

        // Call Graph Assertions
        // Expect edge: MyController.handleRequest -> MyService.doWork
        boolean hasCallEdge = callGraph.getEdges().stream().anyMatch(e -> 
            e.getSource().equals("MyController.handleRequest") && e.getTarget().equals("MyService.doWork"));
        assertTrue(hasCallEdge);

        // Package Tree Assertions
        assertNotNull(packageTree);
        assertEquals("packages", packageTree.getName());
    }
}
