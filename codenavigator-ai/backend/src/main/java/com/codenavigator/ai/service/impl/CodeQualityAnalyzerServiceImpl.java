package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.QualityIssueDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.CodeQualityResult;
import com.codenavigator.ai.repository.CodeQualityResultRepository;
import com.codenavigator.ai.service.CodeQualityAnalyzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.FieldDeclaration;
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
public class CodeQualityAnalyzerServiceImpl implements CodeQualityAnalyzerService {

    private final CodeQualityResultRepository codeQualityResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyze(AnalysisRecord record, File repositoryDir) {
        log.info("Starting AST code quality analysis for repository: {}", record.getRepository().getRepoName());

        try {
            // Configure JavaParser
            StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

            // 1. Gather all files
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            List<QualityIssueDto> issues = new ArrayList<>();

            // 2. Parse depending on file extension
            for (File file : sourceFiles) {
                try {
                    String name = file.getName();
                    if (name.endsWith(".java")) {
                        analyzeJavaFile(file, repositoryDir, issues);
                    } else if (name.endsWith(".py")) {
                        analyzePythonFile(file, repositoryDir, issues);
                    } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                        analyzeJsFile(file, repositoryDir, issues);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file for quality details: {}. Error: {}", file.getName(), e.getMessage());
                }
            }

            // 3. Scan for duplicate code blocks
            detectDuplicateCode(sourceFiles, repositoryDir, issues);

            // 4. Calculate score
            int criticalCount = (int) issues.stream().filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity())).count();
            int warningCount = (int) issues.stream().filter(i -> "WARNING".equalsIgnoreCase(i.getSeverity())).count();
            int infoCount = (int) issues.stream().filter(i -> "INFO".equalsIgnoreCase(i.getSeverity())).count();

            int deduction = (criticalCount * 8) + (warningCount * 3) + (infoCount * 1);
            int qualityScore = Math.max(10, 100 - deduction);

            // 5. Generate Markdown Summary
            String summary = generateQualitySummary(qualityScore, issues);

            // 6. Save results
            CodeQualityResult result = CodeQualityResult.builder()
                    .analysisRecord(record)
                    .qualityScore(qualityScore)
                    .issuesJson(objectMapper.writeValueAsString(issues))
                    .qualitySummary(summary)
                    .build();

            codeQualityResultRepository.save(result);
            log.info("Code Quality analysis successfully saved for record: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to complete quality analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Quality analysis failed: " + e.getMessage(), e);
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

    private void analyzeJavaFile(File file, File rootDir, List<QualityIssueDto> issues) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
        List<String> fileLines = Files.readAllLines(file.toPath());

        // Check naming conventions for Class names
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
            String className = cid.getNameAsString();
            if (!Character.isUpperCase(className.charAt(0))) {
                issues.add(QualityIssueDto.builder()
                        .filePath(relativePath)
                        .line(cid.getBegin().map(b -> b.line).orElse(1))
                        .ruleName("Naming Convention Violation")
                        .severity("WARNING")
                        .description("Class name '" + className + "' should start with an uppercase letter (PascalCase).")
                        .snippet(getSnippet(fileLines, cid.getBegin().map(b -> b.line).orElse(1)))
                        .build());
            }
        });

        // Check fields and variables
        cu.findAll(VariableDeclarator.class).forEach(vd -> {
            String varName = vd.getNameAsString();
            int line = vd.getBegin().map(b -> b.line).orElse(1);

            // Naming check (variables should start with lowercase letter)
            if (varName.length() > 1 && Character.isUpperCase(varName.charAt(0)) && !varName.toUpperCase().equals(varName)) {
                issues.add(QualityIssueDto.builder()
                        .filePath(relativePath)
                        .line(line)
                        .ruleName("Naming Convention Violation")
                        .severity("WARNING")
                        .description("Variable/field name '" + varName + "' should start with a lowercase letter (camelCase).")
                        .snippet(getSnippet(fileLines, line))
                        .build());
            }
        });

        // Check Method violations: Long Methods & High Complexity
        cu.findAll(MethodDeclaration.class).forEach(md -> {
            String methodName = md.getNameAsString();
            int startLine = md.getBegin().map(b -> b.line).orElse(1);
            int endLine = md.getEnd().map(b -> b.line).orElse(1);
            int length = endLine - startLine + 1;

            // Long method checking
            if (length > 30) {
                issues.add(QualityIssueDto.builder()
                        .filePath(relativePath)
                        .line(startLine)
                        .ruleName("Long Method smell")
                        .severity("WARNING")
                        .description("Method '" + methodName + "()' contains " + length + " lines. Consider splitting it into smaller, focused helpers.")
                        .snippet(getSnippet(fileLines, startLine))
                        .build());
            }

            // Cyclomatic complexity estimation
            int complexity = calculateJavaComplexity(md);
            if (complexity > 10) {
                issues.add(QualityIssueDto.builder()
                        .filePath(relativePath)
                        .line(startLine)
                        .ruleName("High Cyclomatic Complexity")
                        .severity("CRITICAL")
                        .description("Method '" + methodName + "()' has complexity score " + complexity + ". Refactor branch logic structures.")
                        .snippet(getSnippet(fileLines, startLine))
                        .build());
            }

            // Unused variables checking inside methods
            md.findAll(VariableDeclarator.class).forEach(vd -> {
                String varName = vd.getNameAsString();
                int varLine = vd.getBegin().map(b -> b.line).orElse(1);
                String methodBody = md.getBody().map(b -> b.toString()).orElse("");
                
                // Count occurrences of variable name in method body
                int occurrences = countSubstringOccurrences(methodBody, varName);
                if (occurrences <= 1) { // Declared but never used again
                    issues.add(QualityIssueDto.builder()
                            .filePath(relativePath)
                            .line(varLine)
                            .ruleName("Unused Local Variable")
                            .severity("INFO")
                            .description("Local variable '" + varName + "' is declared but never referenced in method scope.")
                            .snippet(getSnippet(fileLines, varLine))
                            .build());
                }
            });
        });
    }

    private void analyzePythonFile(File file, File rootDir, List<QualityIssueDto> issues) throws Exception {
        String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
        List<String> lines = Files.readAllLines(file.toPath());

        Pattern classPattern = Pattern.compile("^class\\s+([a-zA-Z0-9_]+)");
        Pattern defPattern = Pattern.compile("^def\\s+([a-zA-Z0-9_]+)\\s*\\(");
        Pattern unusedVarPattern = Pattern.compile("^([a-zA-Z0-9_]+)\\s*=\\s*[^=]");

        int lineIndex = 0;
        int currentMethodStart = -1;
        String currentMethodName = "";
        int currentMethodIndent = -1;
        List<String> methodBodyLines = new ArrayList<>();

        for (String line : lines) {
            lineIndex++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Class name check
            Matcher clsM = classPattern.matcher(trimmed);
            if (clsM.find()) {
                String className = clsM.group(1);
                if (!Character.isUpperCase(className.charAt(0))) {
                    issues.add(QualityIssueDto.builder()
                            .filePath(relativePath)
                            .line(lineIndex)
                            .ruleName("Naming Convention Violation")
                            .severity("WARNING")
                            .description("Python class name '" + className + "' should be CamelCase.")
                            .snippet(getSnippet(lines, lineIndex))
                            .build());
                }
            }

            // Function length checks
            Matcher defM = defPattern.matcher(trimmed);
            if (defM.find()) {
                if (currentMethodStart != -1) {
                    checkPythonMethodLength(relativePath, currentMethodName, currentMethodStart, lineIndex - 1, lines, issues);
                }
                currentMethodStart = lineIndex;
                currentMethodName = defM.group(1);
                // Naming check: Python methods should be snake_case (starts with lowercase)
                if (Character.isUpperCase(currentMethodName.charAt(0))) {
                    issues.add(QualityIssueDto.builder()
                            .filePath(relativePath)
                            .line(lineIndex)
                            .ruleName("Naming Convention Violation")
                            .severity("WARNING")
                            .description("Python function '" + currentMethodName + "' should start with a lowercase letter (snake_case).")
                            .snippet(getSnippet(lines, lineIndex))
                            .build());
                }
            }
        }

        // Check the last method
        if (currentMethodStart != -1) {
            checkPythonMethodLength(relativePath, currentMethodName, currentMethodStart, lines.size(), lines, issues);
        }
    }

    private void checkPythonMethodLength(String path, String name, int start, int end, List<String> lines, List<QualityIssueDto> issues) {
        int len = end - start + 1;
        if (len > 30) {
            issues.add(QualityIssueDto.builder()
                    .filePath(path)
                    .line(start)
                    .ruleName("Long Method smell")
                    .severity("WARNING")
                    .description("Python helper '" + name + "()' has " + len + " lines of code. Split into smaller functions.")
                    .snippet(getSnippet(lines, start))
                    .build());
        }

        // Calculate complexity in this line range
        int complexity = 1;
        for (int i = start - 1; i < end; i++) {
            String l = lines.get(i).trim();
            if (l.startsWith("if ") || l.startsWith("elif ") || l.contains(" for ") || l.contains(" while ") || l.contains(" except ")) {
                complexity++;
            }
        }

        if (complexity > 10) {
            issues.add(QualityIssueDto.builder()
                    .filePath(path)
                    .line(start)
                    .ruleName("High Cyclomatic Complexity")
                    .severity("CRITICAL")
                    .description("Python function '" + name + "()' has complexity score " + complexity + ".")
                    .snippet(getSnippet(lines, start))
                    .build());
        }
    }

    private void analyzeJsFile(File file, File rootDir, List<QualityIssueDto> issues) throws Exception {
        String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
        List<String> lines = Files.readAllLines(file.toPath());

        Pattern funcPattern = Pattern.compile("function\\s+([a-zA-Z0-9_]+)");
        Pattern arrowPattern = Pattern.compile("(?:const|let|var)\\s+([a-zA-Z0-9_]+)\\s*=\\s*\\(.*\\)\\s*=>");

        int lineIndex = 0;
        for (String line : lines) {
            lineIndex++;
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Check complexity/ifs
            Matcher fnM = funcPattern.matcher(trimmed);
            Matcher arrM = arrowPattern.matcher(trimmed);
            String foundName = "";
            if (fnM.find()) foundName = fnM.group(1);
            else if (arrM.find()) foundName = arrM.group(1);

            if (!foundName.isEmpty()) {
                // Variable name check: should be camelCase (starts with lowercase)
                if (Character.isUpperCase(foundName.charAt(0))) {
                    issues.add(QualityIssueDto.builder()
                            .filePath(relativePath)
                            .line(lineIndex)
                            .ruleName("Naming Convention Violation")
                            .severity("WARNING")
                            .description("JavaScript function '" + foundName + "' should start with lowercase letter (camelCase).")
                            .snippet(getSnippet(lines, lineIndex))
                            .build());
                }
            }
        }
    }

    private int calculateJavaComplexity(MethodDeclaration md) {
        int complexity = 1;
        String body = md.getBody().map(b -> b.toString()).orElse("");
        // Count conditionals
        String[] keywords = {"if", "for", "while", "catch", "case", "&&", "||", "?"};
        for (String kw : keywords) {
            complexity += countSubstringOccurrences(body, kw);
        }
        return complexity;
    }

    private int countSubstringOccurrences(String text, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    private void detectDuplicateCode(List<File> sourceFiles, File rootDir, List<QualityIssueDto> issues) {
        try {
            Map<String, String> blockHashes = new HashMap<>(); // Maps string code block to file path
            
            for (File file : sourceFiles) {
                String relativePath = rootDir.toURI().relativize(file.toURI()).getPath();
                List<String> lines = Files.readAllLines(file.toPath());
                if (lines.size() < 10) continue;

                // Loop in sliding window of 6 lines to identify duplicate structural blocks
                for (int i = 0; i <= lines.size() - 6; i += 2) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < 6; j++) {
                        sb.append(lines.get(i + j).trim());
                    }
                    String rawBlock = sb.toString().replace(" ", "").replace("\t", "");
                    if (rawBlock.length() < 30) continue; // Skip trivial lines

                    if (blockHashes.containsKey(rawBlock)) {
                        String matchedFile = blockHashes.get(rawBlock);
                        if (!matchedFile.equals(relativePath)) {
                            issues.add(QualityIssueDto.builder()
                                    .filePath(relativePath)
                                    .line(i + 1)
                                    .ruleName("Duplicate Code smell")
                                    .severity("WARNING")
                                    .description("Duplicate code block detected (6 lines match identical logic inside file: '" + matchedFile + "').")
                                    .snippet(getSnippet(lines, i + 1))
                                    .build());
                            break; // Stop scanning this file to avoid massive spam
                        }
                    } else {
                        blockHashes.put(rawBlock, relativePath);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check duplicate code: {}", e.getMessage());
        }
    }

    private String getSnippet(List<String> lines, int targetLine) {
        int start = Math.max(0, targetLine - 2);
        int end = Math.min(lines.size() - 1, targetLine + 2);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            String prefix = (i + 1 == targetLine) ? " > " : "   ";
            sb.append(prefix).append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String generateQualitySummary(int score, List<QualityIssueDto> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Code Quality Specification Report\n\n");
        sb.append("Calculated Quality Score: **").append(score).append(" / 100**\n\n");
        sb.append("A total of **").append(issues.size()).append("** code smell violations were detected in the repository.\n\n");

        sb.append("## Issues Summary\n\n");
        sb.append("* **Critical Complexity Violations**: ").append(issues.stream().filter(i -> "CRITICAL".equalsIgnoreCase(i.getSeverity())).count()).append("\n");
        sb.append("* **Warning Code Smells (Long Methods, Duplicates)**: ").append(issues.stream().filter(i -> "WARNING".equalsIgnoreCase(i.getSeverity())).count()).append("\n");
        sb.append("* **Info clean-code details (Naming, Unused fields)**: ").append(issues.stream().filter(i -> "INFO".equalsIgnoreCase(i.getSeverity())).count()).append("\n\n");

        sb.append("## Identified Code Smell Catalog\n\n");
        if (issues.isEmpty()) {
            sb.append("Congratulations! No code smells detected. The repository complies with all software engineering naming and design metrics.");
        } else {
            for (QualityIssueDto issue : issues) {
                sb.append("### [").append(issue.getSeverity()).append("] ").append(issue.getRuleName()).append("\n");
                sb.append("* **File**: `").append(issue.getFilePath()).append("` (Line ").append(issue.getLine()).append(")\n");
                sb.append("* **Details**: ").append(issue.getDescription()).append("\n\n");
                sb.append("```java\n").append(issue.getSnippet()).append("```\n\n");
            }
        }

        return sb.toString();
    }
}
