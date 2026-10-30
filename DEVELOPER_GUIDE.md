# Developer Extension Guide

This guide helps developers understand the directory tree and details how to extend the CodeNavigator AI scan rules.

---

## 1. Directory Tree Layout

```text
codenavigator-ai/
├── backend/
│   ├── src/
│   │   ├── main/java/com/codenavigator/ai/
│   │   │   ├── controller/      # REST API Controllers (Auth, Repos, Analyses)
│   │   │   ├── dto/             # Response & request mapping DTOs
│   │   │   ├── entity/          # JPA Hibernate entities definition
│   │   │   ├── repository/      # Spring Data JPA Repository database layers
│   │   │   ├── service/         # Service layer definitions
│   │   │   │   └── impl/        # Core business analyzers (AST/regex scanners)
│   │   └── test/java/...        # Integrated JUnit tests
│   └── pom.xml                  # Maven dependency manager config
└── frontend/
    ├── index.html               # Main SPA view templates
    ├── app.js                   # Client routes, polling, SVG graph rendering
    └── app.css                  # Modern glassmorphic styles
```

---

## 2. Extending Code Quality Rules

Code quality AST checks are defined inside:
**`com.codenavigator.ai.service.impl.CodeQualityAnalyzerServiceImpl`**

### Steps to add a custom Java rule (e.g. check maximum class fields limit):
1. Locate `analyzeJavaFile` method.
2. Query targets inside JavaParser compilation unit `cu`:
```java
cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
    int fieldsCount = cid.getFields().size();
    if (fieldsCount > 15) {
        issues.add(QualityIssueDto.builder()
                .filePath(relativePath)
                .line(cid.getBegin().map(b -> b.line).orElse(1))
                .ruleName("Large Class smell")
                .severity("WARNING")
                .description("Class contains " + fieldsCount + " fields. Consider refactoring data scopes.")
                .snippet(getSnippet(fileLines, cid.getBegin().map(b -> b.line).orElse(1)))
                .build());
    }
});
```

---

## 3. Extending Security Checkers

Code security regex scanners are defined inside:
**`com.codenavigator.ai.service.impl.SecurityAnalyzerServiceImpl`**

### Steps to add a custom security scanner (e.g. checking for dangerous eval functions):
1. Locate `analyzeFileForVulnerabilities` method.
2. Declare matching regex:
```java
Pattern evalPattern = Pattern.compile("eval\\s*\\(");
```
3. Loop file line iterations and match:
```java
Matcher evalM = evalPattern.matcher(trimmed);
if (evalM.find()) {
    vulns.add(VulnerabilityDto.builder()
            .filePath(relativePath)
            .line(lineIndex)
            .vulnType("Code Execution Threat")
            .severity("CRITICAL")
            .description("Dangerous call to 'eval()' detected. Untrusted execution can compromise environment shells.")
            .snippet(getSnippet(lines, lineIndex))
            .resolution("Remove eval() expressions. Use dynamic mapping indexing or safer JSON parsing utilities.")
            .build());
}
```
