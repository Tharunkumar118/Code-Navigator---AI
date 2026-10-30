package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.EndpointDetailsDto;
import com.codenavigator.ai.dto.EndpointParameterDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.ApiDocumentationResult;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.repository.ApiDocumentationResultRepository;
import com.codenavigator.ai.service.impl.ApiDocumentationGeneratorServiceImpl;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

class ApiDocumentationGeneratorServiceTest {

    private ApiDocumentationGeneratorService apiDocumentationGeneratorService;
    private ApiDocumentationResultRepository apiDocumentationResultRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        apiDocumentationResultRepository = Mockito.mock(ApiDocumentationResultRepository.class);
        apiDocumentationGeneratorService = new ApiDocumentationGeneratorServiceImpl(apiDocumentationResultRepository, objectMapper);
    }

    @Test
    void analyze_withRestController_generatesSwaggerDoc(@TempDir File tempDir) throws IOException {
        // 1. Create a dummy Spring REST Controller
        File sourceDir = new File(tempDir, "src/main/java/com/example/controller");
        sourceDir.mkdirs();

        File controllerFile = new File(sourceDir, "ItemController.java");
        Files.writeString(controllerFile.toPath(), 
            "package com.example.controller;\n" +
            "\n" +
            "import org.springframework.web.bind.annotation.*;\n" +
            "\n" +
            "@RestController\n" +
            "@RequestMapping(\"/api/v1\")\n" +
            "public class ItemController {\n" +
            "    \n" +
            "    @PostMapping(\"/items\")\n" +
            "    public String createItem(\n" +
            "            @RequestParam(value = \"name\", defaultValue = \"default\") String name,\n" +
            "            @PathVariable(\"id\") Long id,\n" +
            "            @RequestBody Object body) {\n" +
            "        return \"Success\";\n" +
            "    }\n" +
            "}"
        );

        // Mock AnalysisRecord
        CodeRepository repo = new CodeRepository();
        repo.setRepoName("ItemManager");

        AnalysisRecord record = new AnalysisRecord();
        record.setId(88L);
        record.setRepository(repo);

        // 2. Run documentation generator
        apiDocumentationGeneratorService.analyze(record, tempDir);

        // 3. Capture saved result and assert structures
        ArgumentCaptor<ApiDocumentationResult> captor = ArgumentCaptor.forClass(ApiDocumentationResult.class);
        verify(apiDocumentationResultRepository).save(captor.capture());
        ApiDocumentationResult savedResult = captor.getValue();

        assertNotNull(savedResult);
        assertEquals(record, savedResult.getAnalysisRecord());
        assertEquals(1, savedResult.getEndpointsCount());

        // Deserialize outputs
        List<EndpointDetailsDto> endpoints = objectMapper.readValue(savedResult.getEndpointsJson(), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, EndpointDetailsDto.class));
        Map<String, Object> openapiSpec = objectMapper.readValue(savedResult.getOpenapiDoc(), Map.class);

        // Endpoint validations
        assertNotNull(endpoints);
        assertEquals(1, endpoints.size());
        EndpointDetailsDto ep = endpoints.get(0);
        assertEquals("ItemController", ep.getControllerName());
        assertEquals("createItem", ep.getMethodName());
        assertEquals("POST", ep.getHttpMethod());
        assertEquals("/api/v1/items", ep.getPath());
        assertEquals("Object", ep.getRequestBodyType());
        assertEquals("String", ep.getResponseType());

        // Parameters validations
        List<EndpointParameterDto> params = ep.getParameters();
        assertEquals(3, params.size());

        EndpointParameterDto queryParam = params.stream().filter(p -> "query".equals(p.getIn())).findFirst().orElse(null);
        assertNotNull(queryParam);
        assertEquals("name", queryParam.getName());
        assertEquals("String", queryParam.getType());
        assertEquals("default", queryParam.getDefaultValue());

        EndpointParameterDto pathParam = params.stream().filter(p -> "path".equals(p.getIn())).findFirst().orElse(null);
        assertNotNull(pathParam);
        assertEquals("id", pathParam.getName());
        assertEquals("Long", pathParam.getType());

        // OpenAPI Specification validations
        assertNotNull(openapiSpec);
        assertEquals("3.0.1", openapiSpec.get("openapi"));
        Map<String, Object> info = (Map<String, Object>) openapiSpec.get("info");
        assertEquals("ItemManager API Documentation", info.get("title"));

        Map<String, Object> paths = (Map<String, Object>) openapiSpec.get("paths");
        assertTrue(paths.containsKey("/api/v1/items"));
        Map<String, Object> pathSpec = (Map<String, Object>) paths.get("/api/v1/items");
        assertTrue(pathSpec.containsKey("post"));
    }
}
