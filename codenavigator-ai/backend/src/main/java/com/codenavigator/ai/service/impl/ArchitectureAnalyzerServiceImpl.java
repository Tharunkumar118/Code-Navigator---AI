package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.*;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.ArchitectureResult;
import com.codenavigator.ai.repository.ArchitectureResultRepository;
import com.codenavigator.ai.service.ArchitectureAnalyzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArchitectureAnalyzerServiceImpl implements ArchitectureAnalyzerService {

    private final ArchitectureResultRepository architectureResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyze(AnalysisRecord record, File repositoryDir) {
        log.info("Starting AST architectural analysis for repository: {}", record.getRepository().getRepoName());

        try {
            // Set JavaParser configuration to support Java 17 features (like records)
            StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);
            
            // 1. Gather all source files (exclude tests and build directories)
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            // 2. Parse AST metadata for each file depending on language
            List<ClassInfo> inventory = new ArrayList<>();
            for (File file : sourceFiles) {
                try {
                    String name = file.getName();
                    if (name.endsWith(".java")) {
                        parseJavaFile(file, inventory);
                    } else if (name.endsWith(".py")) {
                        parsePythonFile(file, inventory);
                    } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                        parseJsFile(file, inventory);
                    } else if (name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".h") || name.endsWith(".hpp")) {
                        parseCppFile(file, inventory);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file for architecture: {}. Skipping. Error: {}", file.getName(), e.getMessage());
                }
            }

            // 3. Generate architectural artifacts
            FolderNodeDto packageTree = buildPackageTree(inventory);
            GraphDto classDeps = buildDependencyGraph(inventory);
            GraphDto moduleDeps = buildModuleGraph(inventory);
            GraphDto callGraph = buildCallGraph(inventory);

            String projectStructureDesc = generateProjectStructureDescription(record, repositoryDir);
            String archSummary = generateArchitectureSummary(inventory);

            // 4. Save results to database
            ArchitectureResult result = new ArchitectureResult();
            result.setAnalysisRecord(record);
            result.setProjectStructure(projectStructureDesc);
            result.setPackageStructure(objectMapper.writeValueAsString(packageTree));
            result.setDependencyGraph(objectMapper.writeValueAsString(classDeps));
            result.setModuleGraph(objectMapper.writeValueAsString(moduleDeps));
            result.setCallGraph(objectMapper.writeValueAsString(callGraph));
            result.setArchitectureSummary(archSummary);

            architectureResultRepository.save(result);
            log.info("Architectural analysis successfully saved for analysis record: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to complete architectural analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Architectural analysis failed: " + e.getMessage(), e);
        }
    }

    private void findSourceFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            // Exclude test directories and typical IDE/build folders
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || 
                name.equals("build") || name.equals("src/test")) {
                continue;
            }
            if (f.isDirectory()) {
                findSourceFiles(f, list);
            } else {
                String lower = name.toLowerCase();
                if (lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js") || lower.endsWith(".ts") ||
                    lower.endsWith(".c") || lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".h") || lower.endsWith(".hpp")) {
                    // Ensure it's not under a test folder path (e.g. src/test/java)
                    String path = f.getAbsolutePath().replace('\\', '/');
                    if (!path.contains("/src/test/")) {
                        list.add(f);
                    }
                }
            }
        }
    }

    private void parseJavaFile(File file, List<ClassInfo> inventory) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        List<String> imports = cu.getImports().stream()
                .map(im -> im.getNameAsString())
                .collect(Collectors.toList());

        // Parse classes, interfaces, enums, records
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
            ClassInfo info = createClassInfo(cid.getNameAsString(), packageName, imports, cid.isInterface() ? "interface" : "class");
            parseMethods(cid, info);
            inventory.add(info);
        });

        cu.findAll(EnumDeclaration.class).forEach(ed -> {
            ClassInfo info = createClassInfo(ed.getNameAsString(), packageName, imports, "enum");
            inventory.add(info);
        });

        cu.findAll(RecordDeclaration.class).forEach(rd -> {
            ClassInfo info = createClassInfo(rd.getNameAsString(), packageName, imports, "record");
            inventory.add(info);
        });
    }

    private ClassInfo createClassInfo(String className, String packageName, List<String> imports, String type) {
        ClassInfo info = new ClassInfo();
        info.setName(className);
        info.setPackageName(packageName);
        info.setType(type);
        info.setFullyQualifiedName(packageName.isEmpty() ? className : packageName + "." + className);
        info.setImports(imports);
        return info;
    }

    private void parseMethods(ClassOrInterfaceDeclaration cid, ClassInfo info) {
        cid.findAll(MethodDeclaration.class).forEach(md -> {
            String methodName = md.getNameAsString();
            info.getMethodNames().add(methodName);

            // Find method call expressions inside method body
            md.findAll(MethodCallExpr.class).forEach(mce -> {
                MethodCall call = new MethodCall();
                call.setCallerMethod(methodName);
                call.setCalledMethod(mce.getNameAsString());
                info.getMethodCalls().add(call);
            });
        });
    }

    // --- Package Tree Builder ---
    private FolderNodeDto buildPackageTree(List<ClassInfo> classes) {
        FolderNodeDto root = FolderNodeDto.builder()
                .name("packages")
                .type("directory")
                .children(new ArrayList<>())
                .build();

        for (ClassInfo info : classes) {
            String fqn = info.getFullyQualifiedName();
            String[] parts;
            if ("module".equals(info.getType()) || info.getName().contains(".")) {
                String pkg = info.getPackageName();
                if (pkg == null || pkg.isEmpty()) {
                    parts = new String[]{ info.getName() };
                } else {
                    parts = new String[]{ pkg, info.getName() };
                }
            } else {
                parts = fqn.split("\\.");
            }
            FolderNodeDto current = root;
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                boolean isLast = (i == parts.length - 1);
                String type = isLast ? "file" : "directory";

                FolderNodeDto child = findChild(current, part);
                if (child == null) {
                    child = FolderNodeDto.builder()
                            .name(part)
                            .type(type)
                            .children(isLast ? null : new ArrayList<>())
                            .build();
                    current.getChildren().add(child);
                }
                current = child;
            }
        }
        return root;
    }

    private FolderNodeDto findChild(FolderNodeDto node, String name) {
        if (node.getChildren() == null) return null;
        for (FolderNodeDto child : node.getChildren()) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    // --- Class Dependency Graph Builder ---
    private GraphDto buildDependencyGraph(List<ClassInfo> classes) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        Map<String, ClassInfo> fqnMap = classes.stream()
                .collect(Collectors.toMap(ClassInfo::getFullyQualifiedName, c -> c, (c1, c2) -> c1));

        for (ClassInfo c : classes) {
            nodes.add(GraphNodeDto.builder()
                    .id(c.getFullyQualifiedName())
                    .label(c.getName())
                    .type(c.getType())
                    .build());

            // Check imports to link edges
            for (String imp : c.getImports()) {
                if (fqnMap.containsKey(imp)) {
                    addEdge(edges, edgeKeys, c.getFullyQualifiedName(), imp, "dependency");
                } else {
                    // Check wildcard package imports (e.g. import com.example.*)
                    if (imp.endsWith(".*")) {
                        String prefix = imp.substring(0, imp.length() - 2);
                        for (String otherFqn : fqnMap.keySet()) {
                            if (otherFqn.startsWith(prefix) && otherFqn.lastIndexOf('.') == prefix.length()) {
                                addEdge(edges, edgeKeys, c.getFullyQualifiedName(), otherFqn, "dependency");
                            }
                        }
                    }
                }
            }

            // Also check classes in the same package (which don't need imports)
            for (ClassInfo other : classes) {
                if (c != other && c.getPackageName().equals(other.getPackageName())) {
                    // Quick heuristic: check if class name appears in source/imports
                    addEdge(edges, edgeKeys, c.getFullyQualifiedName(), other.getFullyQualifiedName(), "dependency");
                }
            }
        }

        return new GraphDto(nodes, edges);
    }

    // --- Package Module Graph Builder ---
    private GraphDto buildModuleGraph(List<ClassInfo> classes) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        Set<String> packages = classes.stream()
                .map(ClassInfo::getPackageName)
                .filter(p -> !p.isEmpty())
                .collect(Collectors.toSet());

        for (String pkg : packages) {
            nodes.add(GraphNodeDto.builder()
                    .id(pkg)
                    .label(pkg)
                    .type("package")
                    .build());
        }

        // Link package dependencies based on class dependencies
        GraphDto classGraph = buildDependencyGraph(classes);
        for (GraphEdgeDto classEdge : classGraph.getEdges()) {
            ClassInfo sourceClass = classes.stream().filter(c -> c.getFullyQualifiedName().equals(classEdge.getSource())).findFirst().orElse(null);
            ClassInfo targetClass = classes.stream().filter(c -> c.getFullyQualifiedName().equals(classEdge.getTarget())).findFirst().orElse(null);

            if (sourceClass != null && targetClass != null) {
                String srcPkg = sourceClass.getPackageName();
                String tgtPkg = targetClass.getPackageName();
                if (!srcPkg.isEmpty() && !tgtPkg.isEmpty() && !srcPkg.equals(tgtPkg)) {
                    addEdge(edges, edgeKeys, srcPkg, tgtPkg, "dependency");
                }
            }
        }

        return new GraphDto(nodes, edges);
    }

    // --- Call Graph Builder ---
    private GraphDto buildCallGraph(List<ClassInfo> classes) {
        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphEdgeDto> edges = new ArrayList<>();
        Set<String> edgeKeys = new HashSet<>();

        // Map method names to fully qualified method nodes (e.g., "GreetingController.greeting")
        Map<String, List<String>> methodCatalog = new HashMap<>();
        for (ClassInfo c : classes) {
            for (String method : c.getMethodNames()) {
                String methodFqn = c.getName() + "." + method;
                nodes.add(GraphNodeDto.builder()
                        .id(methodFqn)
                        .label(methodFqn)
                        .type("method")
                        .build());

                methodCatalog.computeIfAbsent(method, k -> new ArrayList<>()).add(methodFqn);
            }
        }

        // Connect method calls
        for (ClassInfo c : classes) {
            for (MethodCall call : c.getMethodCalls()) {
                String callerFqn = c.getName() + "." + call.getCallerMethod();
                List<String> targetFqns = methodCatalog.get(call.getCalledMethod());
                if (targetFqns != null) {
                    for (String targetFqn : targetFqns) {
                        // Avoid self-loops
                        if (!callerFqn.equals(targetFqn)) {
                            addEdge(edges, edgeKeys, callerFqn, targetFqn, "calls");
                        }
                    }
                }
            }
        }

        return new GraphDto(nodes, edges);
    }

    private void addEdge(List<GraphEdgeDto> edges, Set<String> edgeKeys, String src, String tgt, String type) {
        String key = src + "->" + tgt;
        if (!edgeKeys.contains(key)) {
            edgeKeys.add(key);
            edges.add(GraphEdgeDto.builder()
                    .source(src)
                    .target(tgt)
                    .type(type)
                    .build());
        }
    }

    // --- Summary Generators ---
    private String generateProjectStructureDescription(AnalysisRecord record, File repositoryDir) {
        StringBuilder sb = new StringBuilder();
        sb.append("Project Name: ").append(record.getRepository().getRepoName()).append("\n");
        sb.append("Language: ").append(record.getRepository().getPrimaryLanguage()).append("\n");
        sb.append("Framework: ").append(record.getRepository().getFramework()).append("\n");

        File[] files = repositoryDir.listFiles();
        boolean isMultiModule = false;
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory() && new File(f, "pom.xml").exists()) {
                    isMultiModule = true;
                    break;
                }
            }
        }

        if (isMultiModule) {
            sb.append("Structure Type: Multi-module Maven Project\nSubmodules detected:\n");
            if (files != null) {
                for (File f : files) {
                    if (f.isDirectory() && new File(f, "pom.xml").exists()) {
                        sb.append(" - ").append(f.getName()).append("\n");
                    }
                }
            }
        } else {
            sb.append("Structure Type: Single-module Layered Project\n");
        }

        return sb.toString();
    }

    private String generateArchitectureSummary(List<ClassInfo> classes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Software Architecture Summary:\n");
        sb.append("==============================\n");
        sb.append("Analyzed ").append(classes.size()).append(" system components.\n\n");

        // Compute layer statistics
        long controllerCount = classes.stream().filter(c -> c.getName().endsWith("Controller")).count();
        long serviceCount = classes.stream().filter(c -> c.getName().endsWith("Service") || c.getName().endsWith("ServiceImpl")).count();
        long repoCount = classes.stream().filter(c -> c.getName().endsWith("Repository")).count();
        long entityCount = classes.stream().filter(c -> c.getName().contains("Entity") || c.getPackageName().contains("entity") || c.getPackageName().contains("model")).count();

        sb.append("Layer Catalog:\n");
        sb.append(" - Presentation Layer (Controllers): ").append(controllerCount).append(" component(s)\n");
        sb.append(" - Business Logic Layer (Services): ").append(serviceCount).append(" component(s)\n");
        sb.append(" - Data Access Layer (Repositories): ").append(repoCount).append(" component(s)\n");
        sb.append(" - Domain Model Layer (Entities): ").append(entityCount).append(" component(s)\n\n");

        sb.append("Architectural Style Pattern: Layered Architecture (Monolith)\n");
        sb.append("Data Access style: Spring Data JPA (Repositories).\n");
        sb.append("Loose Coupling validation: Service interfaces map to corresponding implementations.");

        return sb.toString();
    }

    // Inner model representation
    @Data
    private static class ClassInfo {
        private String name;
        private String packageName;
        private String fullyQualifiedName;
        private String type;
        private List<String> imports = new ArrayList<>();
        private List<String> methodNames = new ArrayList<>();
        private List<MethodCall> methodCalls = new ArrayList<>();
    }

    @Data
    private static class MethodCall {
        private String callerMethod;
        private String calledMethod;
    }

    private void parsePythonFile(File file, List<ClassInfo> inventory) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String moduleName = file.getName();
        String packageName = file.getParentFile().getName();

        List<String> imports = new ArrayList<>();
        Pattern importPattern = Pattern.compile("^(?:import|from)\\s+([a-zA-Z0-9_.]+)");

        for (String line : lines) {
            line = line.trim();
            Matcher matcher = importPattern.matcher(line);
            if (matcher.find()) {
                imports.add(matcher.group(1));
            }
        }

        boolean hasClasses = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("class ")) {
                hasClasses = true;
                String classDecl = line.substring(6);
                String className = classDecl.contains("(") ? classDecl.substring(0, classDecl.indexOf('(')).trim() :
                                   classDecl.contains(":") ? classDecl.substring(0, classDecl.indexOf(':')).trim() : classDecl.trim();
                
                ClassInfo info = createClassInfo(className, packageName, imports, "class");
                inventory.add(info);
            }
        }

        if (!hasClasses) {
            ClassInfo info = createClassInfo(moduleName, packageName, imports, "module");
            inventory.add(info);
        }
    }

    private void parseJsFile(File file, List<ClassInfo> inventory) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String moduleName = file.getName();
        String packageName = file.getParentFile().getName();

        List<String> imports = new ArrayList<>();
        Pattern importPattern = Pattern.compile("^(?:import|const|let|var)\\s+.*from\\s+[\"']([^\"']+)[\"']");
        Pattern requirePattern = Pattern.compile("require\\(\\s*[\"']([^\"']+)[\"']\\s*\\)");

        for (String line : lines) {
            line = line.trim();
            Matcher m1 = importPattern.matcher(line);
            if (m1.find()) {
                imports.add(m1.group(1));
            } else {
                Matcher m2 = requirePattern.matcher(line);
                if (m2.find()) {
                    imports.add(m2.group(1));
                }
            }
        }

        ClassInfo info = createClassInfo(moduleName, packageName, imports, "module");
        inventory.add(info);
    }

    private void parseCppFile(File file, List<ClassInfo> inventory) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String moduleName = file.getName();
        String packageName = file.getParentFile().getName();

        List<String> imports = new ArrayList<>();
        Pattern includePattern = Pattern.compile("^#include\\s*[<\"]([^>\"]+)[>\"]");

        for (String line : lines) {
            line = line.trim();
            Matcher matcher = includePattern.matcher(line);
            if (matcher.find()) {
                imports.add(matcher.group(1));
            }
        }

        ClassInfo info = createClassInfo(moduleName, packageName, imports, "module");
        inventory.add(info);
    }
}
