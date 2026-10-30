package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.FolderNodeDto;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.entity.RepoStatus;
import com.codenavigator.ai.repository.CodeRepositoryRepository;
import com.codenavigator.ai.service.GitService;
import com.codenavigator.ai.service.ProjectScannerService;
import com.codenavigator.ai.service.RepositoryAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryAnalyzerImpl implements RepositoryAnalyzer {

    private static final int MAX_FOLDER_DEPTH = 4;

    private final CodeRepositoryRepository codeRepositoryRepository;
    private final GitService gitService;
    private final ProjectScannerService projectScannerService;
    private final ObjectMapper objectMapper;

    @Override
    @Async
    @Transactional
    public void cloneAndAnalyze(Long repositoryId) {
        log.info("Starting async clone and scan workflow for repository ID: {}", repositoryId);
        
        CodeRepository repo = codeRepositoryRepository.findById(repositoryId).orElse(null);
        if (repo == null) {
            log.error("Repository with ID {} not found in database. Aborting.", repositoryId);
            return;
        }

        File cloneDir = new File("cloned-repos/repo-" + repositoryId);
        try {
            // 1. Prepare folder and update status to CLONING
            if (cloneDir.exists()) {
                log.info("Cleaning up existing repository directory at: {}", cloneDir.getAbsolutePath());
                deleteDirectory(cloneDir);
            }
            
            repo.setStatus(RepoStatus.CLONING);
            repo.setClonePath(cloneDir.getAbsolutePath());
            repo = codeRepositoryRepository.saveAndFlush(repo);

            // 2. Clone the repository
            gitService.cloneRepository(repo.getRepoUrl(), cloneDir);

            // 3. Update status to ANALYZING
            repo.setStatus(RepoStatus.ANALYZING);
            repo = codeRepositoryRepository.saveAndFlush(repo);

            // 4. Perform basic scans
            log.info("Scanning repository files for ID: {}", repositoryId);
            
            // Calculate size
            long sizeInBytes = calculateDirectorySize(cloneDir);
            repo.setSizeKb(sizeInBytes / 1024);

            // Detect language & framework
            detectLanguageAndFramework(cloneDir, repo);

            // Parse dependencies
            List<String> dependencies = scanDependencies(cloneDir);
            repo.setDependencies(objectMapper.writeValueAsString(dependencies));

            // Extract folder structure (exclude hidden items like .git)
            FolderNodeDto folderTree = buildFolderStructure(cloneDir, 0, MAX_FOLDER_DEPTH);
            repo.setFolderStructure(objectMapper.writeValueAsString(folderTree));

            // Deep scan project components (build tools, config files, controllers, services, etc.)
            projectScannerService.scanRepository(repo, cloneDir);

            // 5. Complete analysis successfully
            repo.setStatus(RepoStatus.COMPLETED);
            codeRepositoryRepository.save(repo);
            log.info("Scan workflow completed successfully for repository ID: {}", repositoryId);

        } catch (Exception e) {
            log.error("Error occurred during async analysis of repository ID: {}. Error: {}", repositoryId, e.getMessage(), e);
            repo.setStatus(RepoStatus.FAILED);
            codeRepositoryRepository.save(repo);
            
            // Clean up directory if it exists and clone failed
            if (cloneDir.exists()) {
                deleteDirectory(cloneDir);
            }
        }
    }

    private void deleteDirectory(File file) {
        File[] contents = file.listFiles();
        if (contents != null) {
            for (File f : contents) {
                deleteDirectory(f);
            }
        }
        file.delete();
    }

    private long calculateDirectorySize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    size += f.length();
                } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                    size += calculateDirectorySize(f);
                }
            }
        }
        return size;
    }

    private void detectLanguageAndFramework(File rootDir, CodeRepository repo) {
        File pomFile = new File(rootDir, "pom.xml");
        File packageJsonFile = new File(rootDir, "package.json");
        File reqFile = new File(rootDir, "requirements.txt");
        
        if (pomFile.exists()) {
            repo.setPrimaryLanguage("Java");
            String content = readFileContent(pomFile);
            if (content.contains("spring-boot")) {
                repo.setFramework("Spring Boot");
            } else {
                repo.setFramework("Maven");
            }
            return;
        }
        
        if (packageJsonFile.exists()) {
            String content = readFileContent(packageJsonFile);
            if (content.contains("\"react\"")) {
                repo.setFramework("React");
            } else if (content.contains("\"express\"")) {
                repo.setFramework("Express");
            } else if (content.contains("\"@angular/core\"")) {
                repo.setFramework("Angular");
            } else {
                repo.setFramework("Node.js");
            }
            
            File tsConfig = new File(rootDir, "tsconfig.json");
            if (tsConfig.exists() || hasExtension(rootDir, ".ts")) {
                repo.setPrimaryLanguage("TypeScript");
            } else {
                repo.setPrimaryLanguage("JavaScript");
            }
            return;
        }
        
        if (reqFile.exists()) {
            repo.setPrimaryLanguage("Python");
            String content = readFileContent(reqFile);
            if (content.contains("django")) {
                repo.setFramework("Django");
            } else if (content.contains("flask")) {
                repo.setFramework("Flask");
            } else if (content.contains("fastapi")) {
                repo.setFramework("FastAPI");
            } else {
                repo.setFramework("Python");
            }
            return;
        }
        
        // Fallback extension scanning
        Map<String, Integer> extCounts = new HashMap<>();
        countExtensions(rootDir, extCounts);
        String topExt = extCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("unknown");
                
        switch (topExt) {
            case "java":
                repo.setPrimaryLanguage("Java");
                repo.setFramework("Java Standard");
                break;
            case "py":
                repo.setPrimaryLanguage("Python");
                repo.setFramework("Python Standard");
                break;
            case "js":
                repo.setPrimaryLanguage("JavaScript");
                repo.setFramework("Web Standard");
                break;
            case "ts":
                repo.setPrimaryLanguage("TypeScript");
                repo.setFramework("Web Standard");
                break;
            case "cpp":
            case "cc":
            case "cxx":
                repo.setPrimaryLanguage("C++");
                repo.setFramework("Native");
                break;
            case "c":
                repo.setPrimaryLanguage("C");
                repo.setFramework("Native");
                break;
            case "go":
                repo.setPrimaryLanguage("Go");
                repo.setFramework("Go Module");
                break;
            case "rs":
                repo.setPrimaryLanguage("Rust");
                repo.setFramework("Cargo");
                break;
            default:
                repo.setPrimaryLanguage("Text / Config");
                repo.setFramework("Static");
                break;
        }
    }

    private String readFileContent(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasExtension(File dir, String ext) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(ext)) {
                    return true;
                }
                if (f.isDirectory() && !f.getName().startsWith(".")) {
                    if (hasExtension(f, ext)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void countExtensions(File dir, Map<String, Integer> counts) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile()) {
                    String name = f.getName();
                    int dotIdx = name.lastIndexOf('.');
                    if (dotIdx > 0 && dotIdx < name.length() - 1) {
                        String ext = name.substring(dotIdx + 1).toLowerCase();
                        counts.put(ext, counts.getOrDefault(ext, 0) + 1);
                    }
                } else if (f.isDirectory() && !f.getName().startsWith(".")) {
                    countExtensions(f, counts);
                }
            }
        }
    }

    private List<String> scanDependencies(File dir) {
        File pomFile = new File(dir, "pom.xml");
        File packageJsonFile = new File(dir, "package.json");
        File reqFile = new File(dir, "requirements.txt");

        if (pomFile.exists()) {
            return parseMavenDependencies(pomFile);
        }
        if (packageJsonFile.exists()) {
            return parseNodeDependencies(packageJsonFile);
        }
        if (reqFile.exists()) {
            return parsePythonDependencies(reqFile);
        }
        return Collections.emptyList();
    }

    private List<String> parseMavenDependencies(File pomFile) {
        List<String> deps = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);
            doc.getDocumentElement().normalize();
            
            NodeList nodeList = doc.getElementsByTagName("dependency");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String groupId = getTagValue("groupId", element);
                    String artifactId = getTagValue("artifactId", element);
                    String version = getTagValue("version", element);
                    if (version != null && !version.isBlank()) {
                        deps.add(groupId + ":" + artifactId + ":" + version.trim());
                    } else {
                        deps.add(groupId + ":" + artifactId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Maven dependencies: {}", e.getMessage());
        }
        return deps;
    }

    private String getTagValue(String tag, Element element) {
        NodeList nodeList = element.getElementsByTagName(tag);
        if (nodeList.getLength() > 0) {
            Node node = nodeList.item(0);
            return node.getTextContent();
        }
        return null;
    }

    private List<String> parseNodeDependencies(File packageJsonFile) {
        List<String> deps = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(packageJsonFile);
            addDepsFromNode(root.get("dependencies"), deps);
            addDepsFromNode(root.get("devDependencies"), deps);
        } catch (Exception e) {
            log.error("Failed to parse package.json dependencies: {}", e.getMessage());
        }
        return deps;
    }

    private void addDepsFromNode(JsonNode node, List<String> list) {
        if (node != null && node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                String version = node.get(name).asText();
                list.add(name + ":" + version);
            });
        }
    }

    private List<String> parsePythonDependencies(File reqFile) {
        List<String> deps = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(reqFile.toPath());
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    deps.add(line);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse requirements.txt dependencies: {}", e.getMessage());
        }
        return deps;
    }

    private FolderNodeDto buildFolderStructure(File dir, int depth, int maxDepth) {
        FolderNodeDto node = FolderNodeDto.builder()
                .name(dir.getName())
                .type("directory")
                .children(new ArrayList<>())
                .build();

        if (depth >= maxDepth) {
            return node;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            // Sort files and folders (folders first, alphabetical)
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File file : files) {
                // Ignore hidden directories like .git or node_modules to keep size readable
                if (file.getName().startsWith(".") || file.getName().equals("node_modules") || file.getName().equals("target")) {
                    continue;
                }
                if (file.isDirectory()) {
                    node.getChildren().add(buildFolderStructure(file, depth + 1, maxDepth));
                } else {
                    node.getChildren().add(FolderNodeDto.builder()
                            .name(file.getName())
                            .type("file")
                            .children(null)
                            .build());
                }
            }
        }
        return node;
    }
}
