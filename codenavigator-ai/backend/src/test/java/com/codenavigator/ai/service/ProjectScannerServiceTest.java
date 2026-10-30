package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.service.impl.ProjectScannerServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProjectScannerServiceTest {

    private ProjectScannerService projectScannerService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        projectScannerService = new ProjectScannerServiceImpl(objectMapper);
    }

    @Test
    void scanRepository_withMockJavaProject_detectsAllComponents(@TempDir File tempDir) throws IOException {
        // 1. Create directory structure and files
        // Root build file
        File pomFile = new File(tempDir, "pom.xml");
        Files.writeString(pomFile.toPath(), "<project><dependencies><dependency>spring-boot</dependency></dependencies></project>");

        // Source packages
        File controllerDir = new File(tempDir, "src/main/java/com/example/demo/controller");
        controllerDir.mkdirs();
        File serviceDir = new File(tempDir, "src/main/java/com/example/demo/service");
        serviceDir.mkdirs();
        File repoDir = new File(tempDir, "src/main/java/com/example/demo/repository");
        repoDir.mkdirs();
        File entityDir = new File(tempDir, "src/main/java/com/example/demo/entity");
        entityDir.mkdirs();
        File utilDir = new File(tempDir, "src/main/java/com/example/demo/util");
        utilDir.mkdirs();
        File resourcesDir = new File(tempDir, "src/main/resources");
        resourcesDir.mkdirs();

        // Write java classes with annotations
        File controllerFile = new File(controllerDir, "MyController.java");
        Files.writeString(controllerFile.toPath(), "package com.example.demo.controller;\n\n@RestController\npublic class MyController {}");

        File serviceFile = new File(serviceDir, "MyService.java");
        Files.writeString(serviceFile.toPath(), "package com.example.demo.service;\n\n@Service\npublic class MyService {}");

        File repoFile = new File(repoDir, "MyRepository.java");
        Files.writeString(repoFile.toPath(), "package com.example.demo.repository;\n\npublic interface MyRepository extends JpaRepository<MyEntity, Long> {}");

        File entityFile = new File(entityDir, "MyEntity.java");
        Files.writeString(entityFile.toPath(), "package com.example.demo.entity;\n\n@Entity\npublic class MyEntity {}");

        File utilFile = new File(utilDir, "StringUtils.java");
        Files.writeString(utilFile.toPath(), "package com.example.demo.util;\n\npublic class StringUtils {}");

        // Write configuration file
        File configFile = new File(resourcesDir, "application.yml");
        Files.writeString(configFile.toPath(), "server:\n  port: 8080");

        // 2. Execute scanner
        CodeRepository repo = new CodeRepository();
        repo.setRepoName("TestMockRepo");
        projectScannerService.scanRepository(repo, tempDir);

        // 3. Verify results
        assertEquals("Maven", repo.getBuildTool());

        // Deserialize results
        List<String> modules = objectMapper.readValue(repo.getModules(), new TypeReference<>() {});
        List<String> configs = objectMapper.readValue(repo.getConfigurationFiles(), new TypeReference<>() {});
        List<String> controllers = objectMapper.readValue(repo.getControllers(), new TypeReference<>() {});
        List<String> services = objectMapper.readValue(repo.getServices(), new TypeReference<>() {});
        List<String> repositories = objectMapper.readValue(repo.getRepositories(), new TypeReference<>() {});
        List<String> entities = objectMapper.readValue(repo.getEntities(), new TypeReference<>() {});
        List<String> utilities = objectMapper.readValue(repo.getUtilities(), new TypeReference<>() {});

        // Asserts
        assertTrue(modules.contains("com.example.demo.controller"));
        assertTrue(modules.contains("com.example.demo.service"));
        
        assertTrue(configs.contains("pom.xml"));
        assertTrue(configs.contains("src/main/resources/application.yml"));

        assertTrue(controllers.contains("com.example.demo.controller.MyController"));
        assertTrue(services.contains("com.example.demo.service.MyService"));
        assertTrue(repositories.contains("com.example.demo.repository.MyRepository"));
        assertTrue(entities.contains("com.example.demo.entity.MyEntity"));
        assertTrue(utilities.contains("com.example.demo.util.StringUtils"));
    }
}
