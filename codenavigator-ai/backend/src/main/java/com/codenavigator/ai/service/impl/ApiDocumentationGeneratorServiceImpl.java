package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.ApiDocumentationResultDto;
import com.codenavigator.ai.dto.EndpointDetailsDto;
import com.codenavigator.ai.dto.EndpointParameterDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.ApiDocumentationResult;
import com.codenavigator.ai.repository.ApiDocumentationResultRepository;
import com.codenavigator.ai.service.ApiDocumentationGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
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
public class ApiDocumentationGeneratorServiceImpl implements ApiDocumentationGeneratorService {

    private final ApiDocumentationResultRepository apiDocumentationResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyze(AnalysisRecord record, File repositoryDir) {
        log.info("Starting AST API documentation generation for repository: {}", record.getRepository().getRepoName());

        try {
            // Configure JavaParser targeting language level JAVA_17
            StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

            // 1. Gather all source files
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            // 2. Parse REST Controllers and Endpoints depending on language
            List<EndpointDetailsDto> endpointsList = new ArrayList<>();
            for (File file : sourceFiles) {
                try {
                    String name = file.getName();
                    if (name.endsWith(".java")) {
                        parseControllerFile(file, endpointsList);
                    } else if (name.endsWith(".py")) {
                        parsePythonControllerFile(file, endpointsList);
                    } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                        parseJsControllerFile(file, endpointsList);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file for API doc: {}. Skipping. Error: {}", file.getName(), e.getMessage());
                }
            }

            // 3. Build OpenAPI Specification Map
            Map<String, Object> openapiSpec = buildOpenApiSpecification(record.getRepository().getRepoName(), endpointsList);

            // 4. Save results to database
            ApiDocumentationResult result = new ApiDocumentationResult();
            result.setAnalysisRecord(record);
            result.setEndpointsCount(endpointsList.size());
            result.setEndpointsJson(objectMapper.writeValueAsString(endpointsList));
            result.setOpenapiDoc(objectMapper.writeValueAsString(openapiSpec));

            apiDocumentationResultRepository.save(result);
            log.info("API documentation generation successfully completed for analysis record: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to generate API documentation: {}", e.getMessage(), e);
            throw new RuntimeException("API documentation generation failed: " + e.getMessage(), e);
        }
    }

    private void findSourceFiles(File dir, List<File> list) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || 
                name.equals("build") || name.equals("test") || name.equals("src/test")) {
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

    private void parseControllerFile(File file, List<EndpointDetailsDto> endpointsList) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
            boolean isRestController = cid.isAnnotationPresent("RestController");
            boolean isController = cid.isAnnotationPresent("Controller");

            if (isRestController || isController) {
                String controllerName = cid.getNameAsString();
                
                // Get class-level base path via @RequestMapping
                String basePath = "";
                Optional<AnnotationExpr> requestMapping = cid.getAnnotationByName("RequestMapping");
                if (requestMapping.isPresent()) {
                    basePath = getAnnotationStringValue(requestMapping.get(), "value");
                }

                // Process endpoints on methods
                final String finalBasePath = basePath;
                cid.getMethods().forEach(md -> {
                    processMethodEndpoints(controllerName, md, finalBasePath, endpointsList);
                });
            }
        });
    }

    private void processMethodEndpoints(String controllerName, MethodDeclaration md, String basePath, List<EndpointDetailsDto> endpointsList) {
        // Supported annotation mappings
        String[] mappings = {"GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping", "RequestMapping"};
        
        for (String mapping : mappings) {
            Optional<AnnotationExpr> annotationOpt = md.getAnnotationByName(mapping);
            if (annotationOpt.isPresent()) {
                AnnotationExpr annot = annotationOpt.get();
                String httpMethod = getHttpMethodFromAnnotation(mapping, annot);
                String subPath = getAnnotationStringValue(annot, "value");
                String fullPath = combinePaths(basePath, subPath);

                EndpointDetailsDto details = new EndpointDetailsDto();
                details.setControllerName(controllerName);
                details.setMethodName(md.getNameAsString());
                details.setHttpMethod(httpMethod);
                details.setPath(fullPath);
                details.setSummary(md.getNameAsString());
                details.setResponseType(md.getType().asString());
                
                // Parse parameters
                List<EndpointParameterDto> params = parseParameters(md);
                details.setParameters(params);

                // Identify RequestBody
                params.stream()
                        .filter(p -> "body".equals(p.getIn()))
                        .findFirst()
                        .ifPresent(p -> details.setRequestBodyType(p.getType()));

                endpointsList.add(details);
            }
        }
    }

    private List<EndpointParameterDto> parseParameters(MethodDeclaration md) {
        List<EndpointParameterDto> list = new ArrayList<>();
        
        for (Parameter p : md.getParameters()) {
            String paramName = p.getNameAsString();
            String paramType = p.getType().asString();
            
            if (p.isAnnotationPresent("PathVariable")) {
                list.add(EndpointParameterDto.builder()
                        .name(paramName)
                        .type(paramType)
                        .in("path")
                        .required(true)
                        .build());
            } else if (p.isAnnotationPresent("RequestParam")) {
                Optional<AnnotationExpr> reqParam = p.getAnnotationByName("RequestParam");
                String customName = reqParam.map(annot -> getAnnotationStringValue(annot, "name")).orElse("");
                if (customName.isEmpty()) {
                    customName = reqParam.map(annot -> getAnnotationStringValue(annot, "value")).orElse("");
                }
                String defaultVal = reqParam.map(annot -> getAnnotationStringValue(annot, "defaultValue")).orElse(null);
                
                list.add(EndpointParameterDto.builder()
                        .name(customName.isEmpty() ? paramName : customName)
                        .type(paramType)
                        .in("query")
                        .required(reqParam.map(annot -> getAnnotationBooleanValue(annot, "required")).orElse(false))
                        .defaultValue(defaultVal)
                        .build());
            } else if (p.isAnnotationPresent("RequestBody")) {
                list.add(EndpointParameterDto.builder()
                        .name(paramName)
                        .type(paramType)
                        .in("body")
                        .required(true)
                        .build());
            } else if (p.isAnnotationPresent("RequestHeader")) {
                Optional<AnnotationExpr> header = p.getAnnotationByName("RequestHeader");
                String customName = header.map(annot -> getAnnotationStringValue(annot, "value")).orElse("");
                list.add(EndpointParameterDto.builder()
                        .name(customName.isEmpty() ? paramName : customName)
                        .type(paramType)
                        .in("header")
                        .required(header.map(annot -> getAnnotationBooleanValue(annot, "required")).orElse(false))
                        .build());
            }
        }
        
        return list;
    }

    private String getHttpMethodFromAnnotation(String annotationName, AnnotationExpr annotation) {
        switch (annotationName) {
            case "GetMapping": return "GET";
            case "PostMapping": return "POST";
            case "PutMapping": return "PUT";
            case "DeleteMapping": return "DELETE";
            case "PatchMapping": return "PATCH";
            case "RequestMapping":
                // Try reading from 'method' attribute (e.g. RequestMethod.POST)
                if (annotation.isNormalAnnotationExpr()) {
                    for (MemberValuePair mvp : annotation.asNormalAnnotationExpr().getPairs()) {
                        if (mvp.getNameAsString().equals("method")) {
                            String methodVal = mvp.getValue().toString();
                            if (methodVal.contains("POST")) return "POST";
                            if (methodVal.contains("PUT")) return "PUT";
                            if (methodVal.contains("DELETE")) return "DELETE";
                            if (methodVal.contains("PATCH")) return "PATCH";
                        }
                    }
                }
                return "GET"; // Default to GET
            default: return "GET";
        }
    }

    private String getAnnotationStringValue(AnnotationExpr annotation, String memberName) {
        if (annotation.isSingleMemberAnnotationExpr()) {
            return cleanQuotes(annotation.asSingleMemberAnnotationExpr().getMemberValue().toString());
        } else if (annotation.isNormalAnnotationExpr()) {
            for (MemberValuePair mvp : annotation.asNormalAnnotationExpr().getPairs()) {
                String name = mvp.getNameAsString();
                if (name.equals(memberName) || (memberName.equals("value") && name.equals("path"))) {
                    return cleanQuotes(mvp.getValue().toString());
                }
            }
        }
        return "";
    }

    private Boolean getAnnotationBooleanValue(AnnotationExpr annotation, String memberName) {
        if (annotation.isNormalAnnotationExpr()) {
            for (MemberValuePair mvp : annotation.asNormalAnnotationExpr().getPairs()) {
                if (mvp.getNameAsString().equals(memberName)) {
                    return Boolean.parseBoolean(mvp.getValue().toString());
                }
            }
        }
        return false;
    }

    private String cleanQuotes(String s) {
        if (s == null) return "";
        return s.replaceAll("^\"|\"$", "").trim();
    }

    private String combinePaths(String base, String sub) {
        String cleanBase = base == null ? "" : base.trim();
        String cleanSub = sub == null ? "" : sub.trim();

        if (cleanBase.endsWith("/")) {
            cleanBase = cleanBase.substring(0, cleanBase.length() - 1);
        }
        if (cleanSub.startsWith("/")) {
            cleanSub = cleanSub.substring(1);
        }

        String path = cleanBase + "/" + cleanSub;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/" : path;
    }

    // --- OpenAPI Builder ---
    private Map<String, Object> buildOpenApiSpecification(String repoName, List<EndpointDetailsDto> endpoints) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("openapi", "3.0.1");

        // Info block
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", repoName + " API Documentation");
        info.put("description", "Auto-generated OpenAPI specification built by CodeNavigator AI.");
        info.put("version", "1.0.0");
        doc.put("info", info);

        // Server block
        List<Map<String, Object>> servers = new ArrayList<>();
        Map<String, Object> localServer = new LinkedHashMap<>();
        localServer.put("url", "http://localhost:8080");
        localServer.put("description", "Development Server");
        servers.add(localServer);
        doc.put("servers", servers);

        // Paths block
        Map<String, Map<String, Object>> paths = new LinkedHashMap<>();
        for (EndpointDetailsDto ep : endpoints) {
            Map<String, Object> pathSpec = paths.computeIfAbsent(ep.getPath(), k -> new LinkedHashMap<>());
            
            Map<String, Object> methodSpec = new LinkedHashMap<>();
            methodSpec.put("summary", ep.getSummary());
            methodSpec.put("operationId", ep.getMethodName());
            methodSpec.put("tags", Collections.singletonList(ep.getControllerName()));

            // Parameters
            List<Map<String, Object>> openapiParams = new ArrayList<>();
            for (EndpointParameterDto p : ep.getParameters()) {
                if ("body".equals(p.getIn())) continue; // Handled below in requestBody

                Map<String, Object> pMap = new LinkedHashMap<>();
                pMap.put("name", p.getName());
                pMap.put("in", p.getIn());
                pMap.put("required", p.getRequired());
                
                Map<String, Object> schema = new LinkedHashMap<>();
                schema.put("type", getOpenApiType(p.getType()));
                if (p.getDefaultValue() != null) {
                    schema.put("default", p.getDefaultValue());
                }
                pMap.put("schema", schema);
                
                openapiParams.add(pMap);
            }
            if (!openapiParams.isEmpty()) {
                methodSpec.put("parameters", openapiParams);
            }

            // RequestBody
            if (ep.getRequestBodyType() != null) {
                Map<String, Object> reqBody = new LinkedHashMap<>();
                reqBody.put("required", true);
                
                Map<String, Object> content = new LinkedHashMap<>();
                Map<String, Object> jsonSchema = new LinkedHashMap<>();
                Map<String, Object> refSchema = new LinkedHashMap<>();
                refSchema.put("type", "object");
                refSchema.put("description", "Payload of type " + ep.getRequestBodyType());
                jsonSchema.put("schema", refSchema);
                
                content.put("application/json", jsonSchema);
                reqBody.put("content", content);
                methodSpec.put("requestBody", reqBody);
            }

            // Responses
            Map<String, Object> responses = new LinkedHashMap<>();
            Map<String, Object> success = new LinkedHashMap<>();
            success.put("description", "Successful Operation");
            
            Map<String, Object> content = new LinkedHashMap<>();
            Map<String, Object> jsonSchema = new LinkedHashMap<>();
            Map<String, Object> refSchema = new LinkedHashMap<>();
            refSchema.put("type", getOpenApiType(ep.getResponseType()).equals("object") ? "object" : getOpenApiType(ep.getResponseType()));
            refSchema.put("description", "Response data of type " + ep.getResponseType());
            jsonSchema.put("schema", refSchema);
            
            content.put("application/json", jsonSchema);
            success.put("content", content);
            responses.put("200", success);
            
            methodSpec.put("responses", responses);
            
            // Add method to path
            pathSpec.put(ep.getHttpMethod().toLowerCase(), methodSpec);
        }
        doc.put("paths", paths);

        // Empty schemas stub to comply with OpenAPI specs
        Map<String, Object> components = new LinkedHashMap<>();
        components.put("schemas", new LinkedHashMap<>());
        doc.put("components", components);

        return doc;
    }

    private String getOpenApiType(String javaType) {
        if (javaType == null) return "string";
        String lower = javaType.toLowerCase();
        if (lower.contains("int") || lower.contains("long") || lower.contains("short")) return "integer";
        if (lower.contains("double") || lower.contains("float") || lower.contains("decimal")) return "number";
        if (lower.contains("boolean")) return "boolean";
        if (lower.contains("list") || lower.contains("set") || lower.endsWith("[]")) return "array";
        if (lower.equals("void")) return "string";
        return "object"; // Custom Class DTOs
    }

    private void parsePythonControllerFile(File file, List<EndpointDetailsDto> endpointsList) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String controllerName = file.getName();

        Pattern fastapiPattern = Pattern.compile("@(?:app|router|api)\\.(get|post|put|delete|patch|options)\\s*\\(\\s*[\"']([^\"']+)[\"']");
        Pattern flaskPattern = Pattern.compile("@(?:app|api|blueprint|bp)\\.route\\s*\\(\\s*[\"']([^\"']+)[\"']");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            Matcher faMatcher = fastapiPattern.matcher(line);
            if (faMatcher.find()) {
                String httpMethod = faMatcher.group(1).toUpperCase();
                String path = faMatcher.group(2);
                
                String nextLine = (i + 1 < lines.size()) ? lines.get(i + 1).trim() : "";
                String handlerMethodName = "handler";
                List<String> queryParams = new ArrayList<>();
                if (nextLine.startsWith("def ")) {
                    String rest = nextLine.substring(4);
                    handlerMethodName = rest.contains("(") ? rest.substring(0, rest.indexOf('(')).trim() : rest.trim();
                    if (rest.contains("(") && rest.contains(")")) {
                        String paramStr = rest.substring(rest.indexOf('(') + 1, rest.indexOf(')'));
                        queryParams = Arrays.stream(paramStr.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty() && !s.equals("self") && !s.equals("cls"))
                                .map(s -> s.contains(":") ? s.substring(0, s.indexOf(':')).trim() : s)
                                .collect(Collectors.toList());
                    }
                }

                List<EndpointParameterDto> parameters = new ArrayList<>();
                for (String p : extractPathParams(path)) {
                    parameters.add(EndpointParameterDto.builder().name(p).type("string").in("path").required(true).build());
                }
                for (String p : queryParams) {
                    if (!extractPathParams(path).contains(p)) {
                        parameters.add(EndpointParameterDto.builder().name(p).type("string").in("query").required(false).build());
                    }
                }

                endpointsList.add(EndpointDetailsDto.builder()
                        .controllerName(controllerName)
                        .httpMethod(httpMethod)
                        .path(path)
                        .methodName(handlerMethodName)
                        .parameters(parameters)
                        .build());
            } else {
                Matcher flMatcher = flaskPattern.matcher(line);
                if (flMatcher.find()) {
                    String path = flMatcher.group(1);
                    String httpMethod = "GET";
                    if (line.contains("methods")) {
                        if (line.contains("POST")) httpMethod = "POST";
                        else if (line.contains("PUT")) httpMethod = "PUT";
                        else if (line.contains("DELETE")) httpMethod = "DELETE";
                    }

                    String nextLine = (i + 1 < lines.size()) ? lines.get(i + 1).trim() : "";
                    String handlerMethodName = "handler";
                    List<String> queryParams = new ArrayList<>();
                    if (nextLine.startsWith("def ")) {
                        String rest = nextLine.substring(4);
                        handlerMethodName = rest.contains("(") ? rest.substring(0, rest.indexOf('(')).trim() : rest.trim();
                        if (rest.contains("(") && rest.contains(")")) {
                            String paramStr = rest.substring(rest.indexOf('(') + 1, rest.indexOf(')'));
                            queryParams = Arrays.stream(paramStr.split(","))
                                    .map(String::trim)
                                    .filter(s -> !s.isEmpty() && !s.equals("self") && !s.equals("cls"))
                                    .map(s -> s.contains(":") ? s.substring(0, s.indexOf(':')).trim() : s)
                                    .collect(Collectors.toList());
                        }
                    }

                    List<EndpointParameterDto> parameters = new ArrayList<>();
                    for (String p : extractPathParams(path)) {
                        parameters.add(EndpointParameterDto.builder().name(p).type("string").in("path").required(true).build());
                    }
                    for (String p : queryParams) {
                        if (!extractPathParams(path).contains(p)) {
                            parameters.add(EndpointParameterDto.builder().name(p).type("string").in("query").required(false).build());
                        }
                    }

                    endpointsList.add(EndpointDetailsDto.builder()
                            .controllerName(controllerName)
                            .httpMethod(httpMethod)
                            .path(path)
                            .methodName(handlerMethodName)
                            .parameters(parameters)
                            .build());
                }
            }
        }
    }

    private void parseJsControllerFile(File file, List<EndpointDetailsDto> endpointsList) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String controllerName = file.getName();

        Pattern expressPattern = Pattern.compile("(?:app|router|routes|api)\\.(get|post|put|delete|patch)\\s*\\(\\s*[\"']([^\"']+)[\"']");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            Matcher matcher = expressPattern.matcher(line);
            if (matcher.find()) {
                String httpMethod = matcher.group(1).toUpperCase();
                String path = matcher.group(2);

                List<EndpointParameterDto> parameters = new ArrayList<>();
                for (String p : extractPathParams(path)) {
                    parameters.add(EndpointParameterDto.builder().name(p).type("string").in("path").required(true).build());
                }

                endpointsList.add(EndpointDetailsDto.builder()
                        .controllerName(controllerName)
                        .httpMethod(httpMethod)
                        .path(path)
                        .methodName("callback")
                        .parameters(parameters)
                        .build());
            }
        }
    }

    private List<String> extractPathParams(String path) {
        List<String> params = new ArrayList<>();
        Pattern p1 = Pattern.compile("\\{([^}]+)\\}");
        Pattern p2 = Pattern.compile("<([^>]+)>");
        Pattern p3 = Pattern.compile(":([a-zA-Z0-9_]+)");

        Matcher m1 = p1.matcher(path);
        while (m1.find()) params.add(m1.group(1));

        Matcher m2 = p2.matcher(path);
        while (m2.find()) {
            String p = m2.group(1);
            if (p.contains(":")) p = p.substring(p.indexOf(':') + 1);
            params.add(p);
        }

        Matcher m3 = p3.matcher(path);
        while (m3.find()) params.add(m3.group(1));

        return params;
    }
}
