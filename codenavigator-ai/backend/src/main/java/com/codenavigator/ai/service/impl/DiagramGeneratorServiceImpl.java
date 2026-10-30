package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.DiagramResult;
import com.codenavigator.ai.repository.DiagramResultRepository;
import com.codenavigator.ai.service.DiagramGeneratorService;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiagramGeneratorServiceImpl implements DiagramGeneratorService {

    private final DiagramResultRepository diagramResultRepository;

    @Override
    @Transactional
    public void generateDiagrams(AnalysisRecord record, File repositoryDir) {
        log.info("Generating system Mermaid diagrams for repository: {}", record.getRepository().getRepoName());

        try {
            // Configure JavaParser
            StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

            // 1. Gather all files
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            // 2. Discover metadata
            List<ScannedClass> classes = new ArrayList<>();
            Map<String, List<String>> packageFiles = new HashMap<>();

            for (File file : sourceFiles) {
                try {
                    String name = file.getName();
                    if (name.endsWith(".java")) {
                        parseJavaClass(file, classes, packageFiles);
                    } else if (name.endsWith(".py")) {
                        parsePythonClass(file, classes, packageFiles);
                    } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                        parseJsClass(file, classes, packageFiles);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file for diagrams: {}. Error: {}", file.getName(), e.getMessage());
                }
            }

            // 3. Compile the 6 Mermaid diagrams
            String classDiagram = generateClassDiagram(classes);
            String packageDiagram = generatePackageDiagram(packageFiles);
            String componentDiagram = generateComponentDiagram(classes);
            String sequenceDiagram = generateSequenceDiagram();
            String useCaseDiagram = generateUseCaseDiagram();
            String architectureDiagram = generateArchitectureDiagram();

            // 4. Save results
            DiagramResult result = DiagramResult.builder()
                    .analysisRecord(record)
                    .classDiagram(classDiagram)
                    .packageDiagram(packageDiagram)
                    .componentDiagram(componentDiagram)
                    .sequenceDiagram(sequenceDiagram)
                    .useCaseDiagram(useCaseDiagram)
                    .architectureDiagram(architectureDiagram)
                    .build();

            diagramResultRepository.save(result);
            log.info("Diagram generation successfully completed for record ID: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to generate diagrams: {}", e.getMessage(), e);
            throw new RuntimeException("Diagram generation failed: " + e.getMessage(), e);
        }
    }

    private void findSourceFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || 
                name.equals("build") || name.equals("src/test")) {
                continue;
            }
            if (f.isDirectory()) {
                findSourceFiles(f, list);
            } else {
                String lower = name.toLowerCase();
                if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts")) {
                    String path = f.getAbsolutePath().replace('\\', '/');
                    if (!path.contains("/src/test/")) {
                        list.add(f);
                    }
                }
            }
        }
    }

    private void parseJavaClass(File file, List<ScannedClass> classes, Map<String, List<String>> packageFiles) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        String packageName = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("default");
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
            String name = cid.getNameAsString();
            ScannedClass sc = new ScannedClass();
            sc.setName(name);
            sc.setPackageName(packageName);
            sc.setIsInterface(cid.isInterface());
            
            // Fields
            cid.findAll(FieldDeclaration.class).forEach(fd -> {
                for (VariableDeclarator vd : fd.getVariables()) {
                    sc.getFields().put(vd.getNameAsString(), vd.getTypeAsString());
                }
            });

            // Methods
            cid.findAll(MethodDeclaration.class).forEach(md -> {
                sc.getMethods().add(md.getNameAsString() + "()");
            });

            // Inheritances
            cid.getExtendedTypes().forEach(et -> sc.getInheritsFrom().add(et.getNameAsString()));
            cid.getImplementedTypes().forEach(it -> sc.getInheritsFrom().add(it.getNameAsString()));

            classes.add(sc);
            packageFiles.computeIfAbsent(packageName, k -> new ArrayList<>()).add(name);
        });
    }

    private void parsePythonClass(File file, List<ScannedClass> classes, Map<String, List<String>> packageFiles) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String moduleName = file.getName().replace(".py", "");
        String packageName = file.getParentFile().getName();

        Pattern classPattern = Pattern.compile("^class\\s+([a-zA-Z0-9_]+)(?:\\(([^)]+)\\))?:");
        Pattern methodPattern = Pattern.compile("^def\\s+([a-zA-Z0-9_]+)\\s*\\(");
        Pattern attributePattern = Pattern.compile("^self\\.([a-zA-Z0-9_]+)\\s*=");

        ScannedClass sc = null;
        for (String line : lines) {
            line = line.trim();
            Matcher clsM = classPattern.matcher(line);
            if (clsM.find()) {
                if (sc != null) classes.add(sc);
                sc = new ScannedClass();
                sc.setName(clsM.group(1));
                sc.setPackageName(packageName);
                sc.setIsInterface(false);
                if (clsM.group(2) != null) {
                    sc.getInheritsFrom().add(clsM.group(2));
                }
                packageFiles.computeIfAbsent(packageName, k -> new ArrayList<>()).add(sc.getName());
            } else if (sc != null) {
                Matcher defM = methodPattern.matcher(line);
                if (defM.find()) {
                    sc.getMethods().add(defM.group(1) + "()");
                }
                Matcher attrM = attributePattern.matcher(line);
                if (attrM.find()) {
                    sc.getFields().put(attrM.group(1), "Object");
                }
            }
        }
        if (sc != null) classes.add(sc);
    }

    private void parseJsClass(File file, List<ScannedClass> classes, Map<String, List<String>> packageFiles) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String moduleName = file.getName().replace(".js", "").replace(".ts", "");
        String packageName = file.getParentFile().getName();

        Pattern classPattern = Pattern.compile("^class\\s+([a-zA-Z0-9_]+)");
        Pattern funcPattern = Pattern.compile("^(?:async\\s+)?function\\s+([a-zA-Z0-9_]+)");
        Pattern methodPattern = Pattern.compile("^([a-zA-Z0-9_]+)\\s*\\(.*\\)\\s*\\{");

        ScannedClass sc = null;
        for (String line : lines) {
            line = line.trim();
            Matcher clsM = classPattern.matcher(line);
            if (clsM.find()) {
                if (sc != null) classes.add(sc);
                sc = new ScannedClass();
                sc.setName(clsM.group(1));
                sc.setPackageName(packageName);
                sc.setIsInterface(false);
                packageFiles.computeIfAbsent(packageName, k -> new ArrayList<>()).add(sc.getName());
            } else if (sc != null) {
                Matcher methM = methodPattern.matcher(line);
                if (methM.find() && !line.startsWith("if") && !line.startsWith("for") && !line.startsWith("while")) {
                    sc.getMethods().add(methM.group(1) + "()");
                }
            }
        }
        if (sc != null) {
            classes.add(sc);
        } else {
            // Mock module class representation if only plain functions are found
            ScannedClass mock = new ScannedClass();
            mock.setName(moduleName);
            mock.setPackageName(packageName);
            mock.setIsInterface(false);
            for (String line : lines) {
                line = line.trim();
                Matcher fnM = funcPattern.matcher(line);
                if (fnM.find()) {
                    mock.getMethods().add(fnM.group(1) + "()");
                }
            }
            if (!mock.getMethods().isEmpty()) {
                classes.add(mock);
                packageFiles.computeIfAbsent(packageName, k -> new ArrayList<>()).add(moduleName);
            }
        }
    }

    private String generateClassDiagram(List<ScannedClass> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("classDiagram\n");
        if (classes.isEmpty()) {
            sb.append("    class EmptyProject {\n        +scan()\n    }\n");
            return sb.toString();
        }

        // Limit size to prevent rendering timeout in Mermaid
        List<ScannedClass> limited = classes.subList(0, Math.min(classes.size(), 25));
        Set<String> classNames = new HashSet<>();
        for (ScannedClass sc : limited) {
            classNames.add(sc.getName());
        }

        for (ScannedClass sc : limited) {
            sb.append("    class ").append(sc.getName()).append(" {\n");
            for (Map.Entry<String, String> f : sc.getFields().entrySet()) {
                sb.append("        +").append(f.getValue()).append(" ").append(f.getKey()).append("\n");
            }
            for (String m : sc.getMethods()) {
                sb.append("        +").append(m).append("\n");
            }
            sb.append("    }\n");

            for (String parent : sc.getInheritsFrom()) {
                if (classNames.contains(parent)) {
                    sb.append("    ").append(parent).append(" <|-- ").append(sc.getName()).append("\n");
                }
            }

            // Simple associations based on field types
            for (Map.Entry<String, String> f : sc.getFields().entrySet()) {
                String type = f.getValue();
                if (classNames.contains(type)) {
                    sb.append("    ").append(sc.getName()).append(" --> ").append(type).append(" : links\n");
                }
            }
        }

        return sb.toString();
    }

    private String generatePackageDiagram(Map<String, List<String>> packageFiles) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TB\n");
        if (packageFiles.isEmpty()) {
            sb.append("    subgraph default\n        EmptyFile\n    end\n");
            return sb.toString();
        }

        int index = 0;
        for (Map.Entry<String, List<String>> pkg : packageFiles.entrySet()) {
            String pkgName = pkg.getKey();
            String cleanPkg = pkgName.replace(".", "_").replace("-", "_");
            sb.append("    subgraph ").append(cleanPkg).append(" [\"Package: ").append(pkgName).append("\"]\n");
            for (String file : pkg.getValue()) {
                sb.append("        ").append(cleanPkg).append("_").append(file).append("[\"").append(file).append("\"]\n");
            }
            sb.append("    end\n\n");
            index++;
            if (index >= 12) break; // Limit packages count
        }

        return sb.toString();
    }

    private String generateComponentDiagram(List<ScannedClass> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");
        sb.append("    Client[\"Web Browser Client\"] --> Gateway[\"JWT Gate / Auth Filter\"]\n");
        
        boolean hasControllers = classes.stream().anyMatch(c -> c.getName().endsWith("Controller"));
        boolean hasServices = classes.stream().anyMatch(c -> c.getName().endsWith("Service") || c.getName().endsWith("ServiceImpl"));
        boolean hasRepos = classes.stream().anyMatch(c -> c.getName().endsWith("Repository"));

        sb.append("    Gateway --> Controller[\"Presentation Controllers\"]\n");
        sb.append("    Controller --> Service[\"Business Logic Services\"]\n");
        sb.append("    Service --> Repository[\"Database Access Repositories\"]\n");
        sb.append("    Repository --> Database[(\"H2 / MySQL Database\")]\n");

        return sb.toString();
    }

    private String generateSequenceDiagram() {
        return "sequenceDiagram\n" +
                "    actor User as Developer/Student\n" +
                "    participant Browser as Web Dashboard\n" +
                "    participant Gate as JWT / Spring Security\n" +
                "    participant Controller as Repositories Controller\n" +
                "    participant Service as Repository Scan Service\n" +
                "    participant Database as MySQL / H2 Database\n\n" +
                "    User->>Browser: Submit Repository URL\n" +
                "    Browser->>Gate: POST /api/v1/repositories (Bearer JWT)\n" +
                "    Gate->>Gate: Validate & Authenticate Token\n" +
                "    Gate->>Controller: Route authorized payload\n" +
                "    Controller->>Service: submitRepositoryScan(url, ownerEmail)\n" +
                "    Service->>Database: Save initial record status QUEUED\n" +
                "    Service-->>Controller: Return Repo Details\n" +
                "    Controller-->>Browser: Return 201 Created (Scan Queued)\n" +
                "    Browser-->>User: Display loading indicator / status table\n";
    }

    private String generateUseCaseDiagram() {
        return "graph LR\n" +
                "    Actor1((Developer User)) --> UC1(Register Repo URL)\n" +
                "    Actor1 --> UC2(Trigger Architecture Scan)\n" +
                "    Actor1 --> UC3(Trigger Database scan)\n" +
                "    Actor1 --> UC4(View Mermaid diagrams)\n" +
                "    Actor1 --> UC5(Trigger Source Code scan)\n" +
                "    Actor2((Platform Admin)) --> UC6(Manage users accounts)\n" +
                "    Actor2 --> UC7(View system health monitors)\n";
    }

    private String generateArchitectureDiagram() {
        return "graph LR\n" +
                "    Browser[\"Web Visualizer (Tailwind/CSS)\"] --> |\"HTTP API / REST\"| Filter[\"Security Gateway / JWT\"]\n" +
                "    Filter --> |\"Authorized Routes\"| App[\"Spring Boot Core Platform\"]\n" +
                "    App --> |\"JPA / Hibernate ORM\"| DB[(\"MySQL / In-Memory H2 DB\")]\n" +
                "    App --> |\"File I/O / JGit\"| RepoStore[[\"Local Cloned Directories Store\"]]\n";
    }

    @lombok.Data
    private static class ScannedClass {
        private String name;
        private String packageName;
        private Boolean isInterface;
        private Map<String, String> fields = new HashMap<>();
        private List<String> methods = new ArrayList<>();
        private List<String> inheritsFrom = new ArrayList<>();
    }
}
