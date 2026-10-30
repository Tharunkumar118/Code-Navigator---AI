package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.*;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.SourceCodeResult;
import com.codenavigator.ai.repository.SourceCodeResultRepository;
import com.codenavigator.ai.service.SourceCodeAnalyzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
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
public class SourceCodeAnalyzerServiceImpl implements SourceCodeAnalyzerService {

    private final SourceCodeResultRepository sourceCodeResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyze(AnalysisRecord record, File repositoryDir) {
        log.info("Starting AST source code analysis for repository: {}", record.getRepository().getRepoName());

        try {
            // Configure JavaParser targeting language level JAVA_17
            StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

            // 1. Gather all source files (exclude tests and build directories)
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            // 2. Parse AST metadata for each file depending on language
            List<ClassDetailsDto> classDetailsList = new ArrayList<>();
            for (File file : sourceFiles) {
                try {
                    String name = file.getName();
                    if (name.endsWith(".java")) {
                        parseJavaFile(file, classDetailsList);
                    } else if (name.endsWith(".py")) {
                        parsePythonFile(file, classDetailsList);
                    } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                        parseJsFile(file, classDetailsList);
                    } else if (name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".h") || name.endsWith(".hpp")) {
                        parseCppFile(file, classDetailsList);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file: {}. Skipping. Error: {}", file.getName(), e.getMessage());
                }
            }

            // 3. Compute inheritance tree
            List<InheritanceNodeDto> inheritanceTree = buildInheritanceTree(classDetailsList);

            // 4. Compute global metrics
            int classesCount = 0;
            int interfacesCount = 0;
            int methodsCount = 0;
            int totalLinesOfCode = 0;
            int totalComplexity = 0;

            for (ClassDetailsDto details : classDetailsList) {
                if (Boolean.TRUE.equals(details.getIsInterface())) {
                    interfacesCount++;
                } else {
                    classesCount++;
                }
                totalLinesOfCode += details.getLinesOfCode();
                methodsCount += details.getMethods().size();
                totalComplexity += details.getMethods().stream()
                        .mapToInt(MethodDetailsDto::getCyclomaticComplexity)
                        .sum();
            }

            double avgComplexity = methodsCount > 0 ? (double) totalComplexity / methodsCount : 1.0;

            // Round to 2 decimal places
            avgComplexity = Math.round(avgComplexity * 100.0) / 100.0;

            // 5. Save results to database
            SourceCodeResult result = new SourceCodeResult();
            result.setAnalysisRecord(record);
            result.setClassesCount(classesCount);
            result.setInterfacesCount(interfacesCount);
            result.setMethodsCount(methodsCount);
            result.setTotalLinesOfCode(totalLinesOfCode);
            result.setAverageCyclomaticComplexity(avgComplexity);
            result.setClassDetails(objectMapper.writeValueAsString(classDetailsList));
            result.setInheritanceTree(objectMapper.writeValueAsString(inheritanceTree));

            sourceCodeResultRepository.save(result);
            log.info("Source code analysis successfully saved for analysis record: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to complete source code analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Source code analysis failed: " + e.getMessage(), e);
        }
    }

    private void findSourceFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            // Exclude test directories and typical IDE/build folders
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || 
                name.equals("build") || name.equals("test") || name.equals("src/test")) {
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

    private void parseJavaFile(File file, List<ClassDetailsDto> classDetailsList) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        // Parse classes and interfaces
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
            ClassDetailsDto details = new ClassDetailsDto();
            details.setName(cid.getNameAsString());
            details.setFullyQualifiedName(packageName.isEmpty() ? cid.getNameAsString() : packageName + "." + cid.getNameAsString());
            details.setType(cid.isInterface() ? "interface" : "class");
            details.setIsInterface(cid.isInterface());
            details.setIsAbstract(cid.isAbstract());
            
            details.setSuperClass(cid.getExtendedTypes().stream()
                    .map(et -> et.getNameAsString())
                    .findFirst()
                    .orElse(""));
            
            details.setImplementedInterfaces(cid.getImplementedTypes().stream()
                    .map(it -> it.getNameAsString())
                    .collect(Collectors.toList()));

            details.setFields(parseFields(cid));
            details.setMethods(parseMethods(cid));
            
            int loc = cid.getRange().map(range -> range.end.line - range.begin.line + 1).orElse(0);
            details.setLinesOfCode(loc);

            classDetailsList.add(details);
        });

        // Parse Enums
        cu.findAll(EnumDeclaration.class).forEach(ed -> {
            ClassDetailsDto details = new ClassDetailsDto();
            details.setName(ed.getNameAsString());
            details.setFullyQualifiedName(packageName.isEmpty() ? ed.getNameAsString() : packageName + "." + ed.getNameAsString());
            details.setType("enum");
            details.setIsInterface(false);
            details.setIsAbstract(false);
            details.setSuperClass("");
            details.setImplementedInterfaces(ed.getImplementedTypes().stream()
                    .map(it -> it.getNameAsString())
                    .collect(Collectors.toList()));
            
            List<FieldDetailsDto> fields = ed.getEntries().stream()
                    .map(entry -> FieldDetailsDto.builder()
                            .name(entry.getNameAsString())
                            .type("EnumConstant")
                            .modifiers("public static final")
                            .build())
                    .collect(Collectors.toList());
            details.setFields(fields);
            details.setMethods(parseMethods(ed));

            int loc = ed.getRange().map(range -> range.end.line - range.begin.line + 1).orElse(0);
            details.setLinesOfCode(loc);

            classDetailsList.add(details);
        });

        // Parse Records
        cu.findAll(RecordDeclaration.class).forEach(rd -> {
            ClassDetailsDto details = new ClassDetailsDto();
            details.setName(rd.getNameAsString());
            details.setFullyQualifiedName(packageName.isEmpty() ? rd.getNameAsString() : packageName + "." + rd.getNameAsString());
            details.setType("record");
            details.setIsInterface(false);
            details.setIsAbstract(false);
            details.setSuperClass("");
            details.setImplementedInterfaces(rd.getImplementedTypes().stream()
                    .map(it -> it.getNameAsString())
                    .collect(Collectors.toList()));

            List<FieldDetailsDto> fields = rd.getParameters().stream()
                    .map(p -> FieldDetailsDto.builder()
                            .name(p.getNameAsString())
                            .type(p.getType().asString())
                            .modifiers("private final")
                            .build())
                    .collect(Collectors.toList());
            details.setFields(fields);
            details.setMethods(parseMethods(rd));

            int loc = rd.getRange().map(range -> range.end.line - range.begin.line + 1).orElse(0);
            details.setLinesOfCode(loc);

            classDetailsList.add(details);
        });
    }

    private List<FieldDetailsDto> parseFields(TypeDeclaration<?> td) {
        List<FieldDetailsDto> list = new ArrayList<>();
        td.getFields().forEach(field -> {
            String typeStr = field.getElementType().asString();
            String modifiers = field.getModifiers().stream()
                    .map(m -> m.getKeyword().asString())
                    .collect(Collectors.joining(" "));
            
            field.getVariables().forEach(v -> {
                list.add(FieldDetailsDto.builder()
                        .name(v.getNameAsString())
                        .type(typeStr)
                        .modifiers(modifiers)
                        .build());
            });
        });
        return list;
    }

    private List<MethodDetailsDto> parseMethods(TypeDeclaration<?> td) {
        List<MethodDetailsDto> list = new ArrayList<>();
        td.getMethods().forEach(method -> {
            String methodName = method.getNameAsString();
            String returnType = method.getType().asString();
            List<String> parameters = method.getParameters().stream()
                    .map(p -> p.getType().asString() + " " + p.getNameAsString())
                    .collect(Collectors.toList());
            String modifiers = method.getModifiers().stream()
                    .map(m -> m.getKeyword().asString())
                    .collect(Collectors.joining(" "));

            int loc = method.getRange().map(range -> range.end.line - range.begin.line + 1).orElse(0);
            int complexity = calculateCyclomaticComplexity(method);

            list.add(MethodDetailsDto.builder()
                    .name(methodName)
                    .returnType(returnType)
                    .parameters(parameters)
                    .modifiers(modifiers)
                    .linesOfCode(loc)
                    .cyclomaticComplexity(complexity)
                    .build());
        });
        return list;
    }

    private int calculateCyclomaticComplexity(MethodDeclaration md) {
        if (md.getBody().isEmpty()) {
            return 0; // Interfaces/abstract methods have complexity 0
        }

        final int[] complexity = {1}; // Base complexity is 1

        md.getBody().get().walk(node -> {
            if (node instanceof com.github.javaparser.ast.stmt.IfStmt) {
                complexity[0]++;
            } else if (node instanceof com.github.javaparser.ast.stmt.ForStmt || node instanceof com.github.javaparser.ast.stmt.ForEachStmt) {
                complexity[0]++;
            } else if (node instanceof com.github.javaparser.ast.stmt.WhileStmt || node instanceof com.github.javaparser.ast.stmt.DoStmt) {
                complexity[0]++;
            } else if (node instanceof com.github.javaparser.ast.stmt.CatchClause) {
                complexity[0]++;
            } else if (node instanceof com.github.javaparser.ast.expr.ConditionalExpr) {
                complexity[0]++;
            } else if (node instanceof com.github.javaparser.ast.stmt.SwitchEntry) {
                com.github.javaparser.ast.stmt.SwitchEntry se = (com.github.javaparser.ast.stmt.SwitchEntry) node;
                if (se.getLabels() != null && !se.getLabels().isEmpty()) {
                    complexity[0]++;
                }
            } else if (node instanceof com.github.javaparser.ast.expr.BinaryExpr) {
                com.github.javaparser.ast.expr.BinaryExpr be = (com.github.javaparser.ast.expr.BinaryExpr) node;
                if (be.getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.AND || 
                    be.getOperator() == com.github.javaparser.ast.expr.BinaryExpr.Operator.OR) {
                    complexity[0]++;
                }
            }
        });

        return complexity[0];
    }

    private List<InheritanceNodeDto> buildInheritanceTree(List<ClassDetailsDto> classes) {
        Map<String, InheritanceNodeDto> nodeMap = new HashMap<>();

        // Initialize nodes
        for (ClassDetailsDto c : classes) {
            String className = c.getFullyQualifiedName();
            nodeMap.put(className, InheritanceNodeDto.builder()
                    .className(className)
                    .superClass(c.getSuperClass())
                    .subClasses(new ArrayList<>())
                    .build());
        }

        // Establish subClasses references
        List<InheritanceNodeDto> result = new ArrayList<>();
        for (InheritanceNodeDto node : nodeMap.values()) {
            String parent = node.getSuperClass();
            if (parent != null && !parent.isEmpty()) {
                // Find fully qualified or simple class name matching parent
                InheritanceNodeDto parentNode = findParentNode(parent, nodeMap);
                if (parentNode != null) {
                    parentNode.getSubClasses().add(node.getClassName());
                }
            }
            result.add(node);
        }

        return result;
    }

    private InheritanceNodeDto findParentNode(String parent, Map<String, InheritanceNodeDto> nodeMap) {
        // Direct FQN lookup
        if (nodeMap.containsKey(parent)) {
            return nodeMap.get(parent);
        }
        // Match simple class name
        for (Map.Entry<String, InheritanceNodeDto> entry : nodeMap.entrySet()) {
            String key = entry.getKey();
            String simpleName = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
            if (simpleName.equals(parent)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void parsePythonFile(File file, List<ClassDetailsDto> classDetailsList) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        int loc = lines.size();
        String moduleName = file.getName();

        ClassDetailsDto currentClass = null;
        List<MethodDetailsDto> moduleMethods = new ArrayList<>();
        List<FieldDetailsDto> moduleFields = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("class ")) {
                if (currentClass != null) {
                    classDetailsList.add(currentClass);
                }

                currentClass = new ClassDetailsDto();
                currentClass.setType("class");
                currentClass.setLinesOfCode(loc);
                
                String classDecl = line.substring(6);
                if (classDecl.contains("(")) {
                    String name = classDecl.substring(0, classDecl.indexOf('(')).trim();
                    currentClass.setName(name);
                    currentClass.setFullyQualifiedName(moduleName + "." + name);
                    String superClass = classDecl.substring(classDecl.indexOf('(') + 1, classDecl.indexOf(')')).trim();
                    currentClass.setSuperClass(superClass);
                } else {
                    String name = classDecl.contains(":") ? classDecl.substring(0, classDecl.indexOf(':')).trim() : classDecl.trim();
                    currentClass.setName(name);
                    currentClass.setFullyQualifiedName(moduleName + "." + name);
                }
                currentClass.setFields(new ArrayList<>());
                currentClass.setMethods(new ArrayList<>());
                currentClass.setImplementedInterfaces(new ArrayList<>());
            } else if (line.startsWith("def ") || (line.contains("def ") && line.indexOf("def ") < 8)) {
                if (lines.get(i).startsWith("def ")) {
                    if (currentClass != null) {
                        classDetailsList.add(currentClass);
                        currentClass = null;
                    }
                }
                int idx = line.indexOf("def ");
                String funcDecl = line.substring(idx + 4);
                String funcName = funcDecl.contains("(") ? funcDecl.substring(0, funcDecl.indexOf('(')).trim() : funcDecl.trim();
                
                List<String> params = new ArrayList<>();
                if (funcDecl.contains("(") && funcDecl.contains(")")) {
                    String paramStr = funcDecl.substring(funcDecl.indexOf('(') + 1, funcDecl.indexOf(')'));
                    params = Arrays.stream(paramStr.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty() && !s.equals("self") && !s.equals("cls"))
                            .collect(Collectors.toList());
                }

                int complexity = 1;
                int j = i + 1;
                int methodLoc = 1;
                while (j < lines.size()) {
                    String subLine = lines.get(j).trim();
                    if (!subLine.isEmpty() && lines.get(j).charAt(0) != ' ' && lines.get(j).charAt(0) != '\t') {
                        if (subLine.startsWith("def ") || subLine.startsWith("class ")) {
                            break;
                        }
                    }
                    if (subLine.startsWith("if ") || subLine.startsWith("elif ") || subLine.contains(" if ") ||
                        subLine.startsWith("for ") || subLine.startsWith("while ") || subLine.startsWith("except ")) {
                        complexity++;
                    }
                    methodLoc++;
                    j++;
                }

                MethodDetailsDto method = MethodDetailsDto.builder()
                        .name(funcName)
                        .returnType("object")
                        .parameters(params)
                        .modifiers(line.substring(0, idx).trim())
                        .linesOfCode(methodLoc)
                        .cyclomaticComplexity(complexity)
                        .build();

                if (currentClass != null) {
                    currentClass.getMethods().add(method);
                } else {
                    moduleMethods.add(method);
                }
            } else if (line.contains("=") && !line.startsWith("def ") && !line.startsWith("class ")) {
                String fieldName = line.substring(0, line.indexOf('=')).trim();
                if (fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    FieldDetailsDto field = FieldDetailsDto.builder()
                            .name(fieldName)
                            .type("object")
                            .modifiers("public")
                            .build();
                    if (currentClass != null) {
                        currentClass.getFields().add(field);
                    } else {
                        moduleFields.add(field);
                    }
                }
            }
        }

        if (currentClass != null) {
            classDetailsList.add(currentClass);
        } else if (!moduleMethods.isEmpty() || !moduleFields.isEmpty()) {
            ClassDetailsDto mockClass = new ClassDetailsDto();
            mockClass.setName(moduleName);
            mockClass.setFullyQualifiedName(moduleName);
            mockClass.setType("module");
            mockClass.setLinesOfCode(loc);
            mockClass.setFields(moduleFields);
            mockClass.setMethods(moduleMethods);
            mockClass.setImplementedInterfaces(new ArrayList<>());
            classDetailsList.add(mockClass);
        }
    }

    private void parseJsFile(File file, List<ClassDetailsDto> classDetailsList) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        int loc = lines.size();
        String moduleName = file.getName();

        List<MethodDetailsDto> methods = new ArrayList<>();
        List<FieldDetailsDto> fields = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("function ") || line.contains("function(") || line.contains("=>")) {
                String funcName = "anonymous";
                if (line.startsWith("function ")) {
                    String rest = line.substring(9);
                    funcName = rest.contains("(") ? rest.substring(0, rest.indexOf('(')).trim() : rest.trim();
                } else if (line.contains("const ") || line.contains("let ") || line.contains("var ")) {
                    int eqIdx = line.indexOf('=');
                    if (eqIdx != -1) {
                        String prefix = line.substring(0, eqIdx).trim();
                        funcName = prefix.substring(prefix.lastIndexOf(' ') + 1);
                    }
                }

                int complexity = 1;
                int methodLoc = 1;
                int j = i + 1;
                while (j < lines.size()) {
                    String subLine = lines.get(j).trim();
                    if (subLine.startsWith("function ") || subLine.startsWith("class ")) {
                        break;
                    }
                    if (subLine.startsWith("if ") || subLine.contains(" if ") || subLine.startsWith("for ") ||
                        subLine.startsWith("while ") || subLine.startsWith("catch ")) {
                        complexity++;
                    }
                    methodLoc++;
                    j++;
                }

                methods.add(MethodDetailsDto.builder()
                        .name(funcName)
                        .returnType("any")
                        .parameters(new ArrayList<>())
                        .modifiers("public")
                        .linesOfCode(methodLoc)
                        .cyclomaticComplexity(complexity)
                        .build());
            }
        }

        ClassDetailsDto mockClass = new ClassDetailsDto();
        mockClass.setName(moduleName);
        mockClass.setFullyQualifiedName(moduleName);
        mockClass.setType("module");
        mockClass.setLinesOfCode(loc);
        mockClass.setFields(fields);
        mockClass.setMethods(methods);
        mockClass.setImplementedInterfaces(new ArrayList<>());
        classDetailsList.add(mockClass);
    }

    private void parseCppFile(File file, List<ClassDetailsDto> classDetailsList) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        int loc = lines.size();
        String moduleName = file.getName();

        List<MethodDetailsDto> methods = new ArrayList<>();
        List<FieldDetailsDto> fields = new ArrayList<>();

        Pattern fnPattern = Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_*<>&\\s]+)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)\\s*\\{?");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            Matcher matcher = fnPattern.matcher(line);
            if (matcher.find()) {
                String returnType = matcher.group(1).trim();
                String funcName = matcher.group(2).trim();
                String paramStr = matcher.group(3).trim();

                List<String> params = Arrays.stream(paramStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

                int complexity = 1;
                int methodLoc = 1;
                int j = i + 1;
                while (j < lines.size()) {
                    String subLine = lines.get(j).trim();
                    if (subLine.startsWith("class ") || (fnPattern.matcher(subLine).find() && subLine.endsWith("{"))) {
                        break;
                    }
                    if (subLine.startsWith("if ") || subLine.contains(" if ") || subLine.startsWith("for ") ||
                        subLine.startsWith("while ") || subLine.startsWith("catch ") || subLine.startsWith("case ")) {
                        complexity++;
                    }
                    methodLoc++;
                    j++;
                }

                methods.add(MethodDetailsDto.builder()
                        .name(funcName)
                        .returnType(returnType)
                        .parameters(params)
                        .modifiers("public")
                        .linesOfCode(methodLoc)
                        .cyclomaticComplexity(complexity)
                        .build());
            }
        }

        ClassDetailsDto mockClass = new ClassDetailsDto();
        mockClass.setName(moduleName);
        mockClass.setFullyQualifiedName(moduleName);
        mockClass.setType("module");
        mockClass.setLinesOfCode(loc);
        mockClass.setFields(fields);
        mockClass.setMethods(methods);
        mockClass.setImplementedInterfaces(new ArrayList<>());
        classDetailsList.add(mockClass);
    }
}
