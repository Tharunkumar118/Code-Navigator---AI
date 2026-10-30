package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.ClassDetailsDto;
import com.codenavigator.ai.dto.EndpointDetailsDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.ApiDocumentationResult;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.SourceCodeResult;
import com.codenavigator.ai.repository.ApiDocumentationResultRepository;
import com.codenavigator.ai.repository.SourceCodeResultRepository;
import com.codenavigator.ai.service.impl.ApiDocumentationGeneratorServiceImpl;
import com.codenavigator.ai.service.impl.SourceCodeAnalyzerServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
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

class MultiLanguageAnalyzerServiceTest {

    private SourceCodeAnalyzerServiceImpl sourceCodeAnalyzerService;
    private ApiDocumentationGeneratorServiceImpl apiDocumentationGeneratorService;

    private SourceCodeResultRepository sourceCodeResultRepository;
    private ApiDocumentationResultRepository apiDocumentationResultRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        sourceCodeResultRepository = Mockito.mock(SourceCodeResultRepository.class);
        apiDocumentationResultRepository = Mockito.mock(ApiDocumentationResultRepository.class);
        objectMapper = new ObjectMapper();

        sourceCodeAnalyzerService = new SourceCodeAnalyzerServiceImpl(
                sourceCodeResultRepository,
                objectMapper
        );

        apiDocumentationGeneratorService = new ApiDocumentationGeneratorServiceImpl(
                apiDocumentationResultRepository,
                objectMapper
        );
    }

    @Test
    void testAnalyzeMultiLanguageProject(@TempDir File tempDir) throws IOException {
        // Write mock Python file
        File pyFile = new File(tempDir, "calculator.py");
        String pyContent = """
                class BasicCalculator:
                    def add(self, a, b):
                        return a + b
                    
                    def divide(self, a, b):
                        if b == 0:
                            raise ValueError("Division by zero")
                        return a / b
                
                @app.get("/api/v1/calculate")
                def calculate(x: int, y: int):
                    return x + y
                """;
        Files.writeString(pyFile.toPath(), pyContent);

        // Write mock JS file
        File jsFile = new File(tempDir, "server.js");
        String jsContent = """
                function logMessage(msg) {
                    console.log(msg);
                }
                
                app.post("/api/v1/send", (req, res) => {
                    res.send("ok");
                });
                """;
        Files.writeString(jsFile.toPath(), jsContent);

        // Write mock C++ file
        File cppFile = new File(tempDir, "main.cpp");
        String cppContent = """
                int calculateSquare(int x) {
                    if (x < 0) {
                        return 0;
                    }
                    return x * x;
                }
                """;
        Files.writeString(cppFile.toPath(), cppContent);

        // Run Source Code Analyzer
        CodeRepository repo = new CodeRepository();
        repo.setRepoName("MultiLanguageRepo");
        AnalysisRecord record = new AnalysisRecord();
        record.setId(100L);
        record.setRepository(repo);

        sourceCodeAnalyzerService.analyze(record, tempDir);

        ArgumentCaptor<SourceCodeResult> scCaptor = ArgumentCaptor.forClass(SourceCodeResult.class);
        verify(sourceCodeResultRepository).save(scCaptor.capture());
        SourceCodeResult scResult = scCaptor.getValue();

        assertNotNull(scResult);
        assertTrue(scResult.getClassesCount() > 0);

        List<ClassDetailsDto> classes = objectMapper.readValue(scResult.getClassDetails(), new TypeReference<List<ClassDetailsDto>>() {});
        
        // Assert Python class exists
        ClassDetailsDto pyClass = classes.stream()
                .filter(c -> c.getName().equals("BasicCalculator"))
                .findFirst()
                .orElse(null);
        assertNotNull(pyClass);
        assertEquals(2, pyClass.getMethods().size());

        // Assert C++ functions parsed (represented as a module mock class)
        ClassDetailsDto cppClass = classes.stream()
                .filter(c -> c.getName().equals("main.cpp"))
                .findFirst()
                .orElse(null);
        assertNotNull(cppClass);
        assertEquals(1, cppClass.getMethods().size());
        assertEquals("calculateSquare", cppClass.getMethods().get(0).getName());

        // Run API Docs Generator
        apiDocumentationGeneratorService.analyze(record, tempDir);

        ArgumentCaptor<ApiDocumentationResult> apiCaptor = ArgumentCaptor.forClass(ApiDocumentationResult.class);
        verify(apiDocumentationResultRepository).save(apiCaptor.capture());
        ApiDocumentationResult apiResult = apiCaptor.getValue();

        assertNotNull(apiResult);
        assertEquals(2, apiResult.getEndpointsCount());

        List<EndpointDetailsDto> endpoints = objectMapper.readValue(apiResult.getEndpointsJson(), new TypeReference<List<EndpointDetailsDto>>() {});
        
        // Verify Python endpoint exists
        boolean hasPythonEndpoint = endpoints.stream()
                .anyMatch(e -> e.getPath().equals("/api/v1/calculate") && e.getHttpMethod().equals("GET"));
        assertTrue(hasPythonEndpoint);

        // Verify Express endpoint exists
        boolean hasJsEndpoint = endpoints.stream()
                .anyMatch(e -> e.getPath().equals("/api/v1/send") && e.getHttpMethod().equals("POST"));
        assertTrue(hasJsEndpoint);
    }
}
