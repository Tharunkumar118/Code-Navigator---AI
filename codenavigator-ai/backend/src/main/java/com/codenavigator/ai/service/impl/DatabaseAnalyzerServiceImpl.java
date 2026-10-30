package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.dto.DatabaseColumnDto;
import com.codenavigator.ai.dto.DatabaseEntityDto;
import com.codenavigator.ai.entity.AnalysisRecord;
import com.codenavigator.ai.entity.DatabaseResult;
import com.codenavigator.ai.repository.DatabaseResultRepository;
import com.codenavigator.ai.service.DatabaseAnalyzerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
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
public class DatabaseAnalyzerServiceImpl implements DatabaseAnalyzerService {

    private final DatabaseResultRepository databaseResultRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void analyze(AnalysisRecord record, File repositoryDir) {
        log.info("Starting AST database analysis for repository: {}", record.getRepository().getRepoName());

        try {
            // Configure JavaParser
            StaticJavaParser.getParserConfiguration().setLanguageLevel(com.github.javaparser.ParserConfiguration.LanguageLevel.JAVA_17);

            // 1. Gather all files
            List<File> sourceFiles = new ArrayList<>();
            findSourceFiles(repositoryDir, sourceFiles);

            List<DatabaseEntityDto> entities = new ArrayList<>();

            // 2. Parse depending on file extension
            for (File file : sourceFiles) {
                try {
                    String name = file.getName();
                    if (name.endsWith(".java")) {
                        parseJavaEntities(file, entities);
                    } else if (name.endsWith(".py")) {
                        parsePythonEntities(file, entities);
                    } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                        parseJsEntities(file, entities);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse file for database results: {}. Error: {}", file.getName(), e.getMessage());
                }
            }

            // 3. Count relationships
            int relationshipsCount = 0;
            for (DatabaseEntityDto entity : entities) {
                relationshipsCount += entity.getForeignKeys().size();
            }

            // 4. Generate Mermaid ER Diagram
            String mermaidEr = generateMermaidEr(entities);

            // 5. Generate Markdown Summary
            String summary = generateDatabaseSummary(entities, relationshipsCount);

            // 6. Save results
            DatabaseResult result = new DatabaseResult();
            result.setAnalysisRecord(record);
            result.setEntitiesCount(entities.size());
            result.setRelationshipsCount(relationshipsCount);
            result.setEntitiesJson(objectMapper.writeValueAsString(entities));
            result.setErDiagramMermaid(mermaidEr);
            result.setDatabaseSummary(summary);

            databaseResultRepository.save(result);
            log.info("Database analysis successfully completed for record ID: {}", record.getId());

        } catch (Exception e) {
            log.error("Failed to execute database analysis: {}", e.getMessage(), e);
            throw new RuntimeException("Database analysis failed: " + e.getMessage(), e);
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

    private void parseJavaEntities(File file, List<DatabaseEntityDto> entities) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cid -> {
            boolean isEntity = cid.getAnnotationByName("Entity").isPresent() || 
                               cid.getAnnotationByName("Table").isPresent();
            
            if (isEntity) {
                DatabaseEntityDto entity = new DatabaseEntityDto();
                entity.setClassName(cid.getNameAsString());
                
                // Table Name default or annotation value
                String tableName = camelToSnake(cid.getNameAsString()) + "s";
                Optional<AnnotationExpr> tableAnn = cid.getAnnotationByName("Table");
                if (tableAnn.isPresent()) {
                    AnnotationExpr ann = tableAnn.get();
                    if (ann.isNormalAnnotationExpr()) {
                        for (MemberValuePair pair : ann.asNormalAnnotationExpr().getPairs()) {
                            if (pair.getNameAsString().equals("name")) {
                                tableName = pair.getValue().toString().replace("\"", "");
                            }
                        }
                    }
                }
                entity.setTableName(tableName);
                entity.setColumns(new ArrayList<>());
                entity.setPrimaryKeys(new ArrayList<>());
                entity.setForeignKeys(new ArrayList<>());
                entity.setIndexes(new ArrayList<>());

                // Parse Indexes
                if (tableAnn.isPresent() && tableAnn.get().toString().contains("indexes")) {
                    Pattern idxPattern = Pattern.compile("columnList\\s*=\\s*\"([^\"]+)\"");
                    Matcher matcher = idxPattern.matcher(tableAnn.get().toString());
                    while (matcher.find()) {
                        entity.getIndexes().add(matcher.group(1));
                    }
                }

                // Parse fields
                cid.findAll(FieldDeclaration.class).forEach(fd -> {
                    for (VariableDeclarator vd : fd.getVariables()) {
                        String fieldName = vd.getNameAsString();
                        String columnName = camelToSnake(fieldName);
                        String dataType = vd.getTypeAsString();
                        
                        boolean isPk = fd.getAnnotationByName("Id").isPresent();
                        boolean isFk = fd.getAnnotationByName("ManyToOne").isPresent() || 
                                       fd.getAnnotationByName("OneToOne").isPresent();

                        String referencedTable = null;
                        if (isFk) {
                            referencedTable = camelToSnake(dataType) + "s";
                        }

                        // Customize column properties
                        Optional<AnnotationExpr> colAnn = fd.getAnnotationByName("Column");
                        boolean nullable = true;
                        if (colAnn.isPresent()) {
                            String colString = colAnn.get().toString();
                            if (colString.contains("name")) {
                                Pattern namePat = Pattern.compile("name\\s*=\\s*\"([^\"]+)\"");
                                Matcher m = namePat.matcher(colString);
                                if (m.find()) columnName = m.group(1);
                            }
                            if (colString.contains("nullable = false")) {
                                nullable = false;
                            }
                        }

                        DatabaseColumnDto col = DatabaseColumnDto.builder()
                                .columnName(columnName)
                                .fieldName(fieldName)
                                .dataType(dataType)
                                .nullable(nullable)
                                .primaryKey(isPk)
                                .foreignKey(isFk)
                                .referencedTable(referencedTable)
                                .build();

                        entity.getColumns().add(col);
                        if (isPk) {
                            entity.getPrimaryKeys().add(columnName);
                        }
                        if (isFk) {
                            entity.getForeignKeys().add(columnName);
                        }
                    }
                });

                entities.add(entity);
            }
        });
    }

    private void parsePythonEntities(File file, List<DatabaseEntityDto> entities) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String moduleName = file.getName();

        DatabaseEntityDto currentEntity = null;
        Pattern classPattern = Pattern.compile("^class\\s+([a-zA-Z0-9_]+)\\s*\\(");
        Pattern tablenamePattern = Pattern.compile("^__tablename__\\s*=\\s*[\"']([^\"']+)[\"']");
        Pattern columnPattern = Pattern.compile("^([a-zA-Z0-9_]+)\\s*=\\s*(?:db\\.)?Column\\((.*)\\)");
        Pattern fkPattern = Pattern.compile("ForeignKey\\(\\s*[\"']([^\"']+)\\.id[\"']\\s*\\)");

        for (String line : lines) {
            line = line.trim();
            Matcher clsMatcher = classPattern.matcher(line);
            if (clsMatcher.find()) {
                if (currentEntity != null) {
                    entities.add(currentEntity);
                }
                currentEntity = new DatabaseEntityDto();
                String className = clsMatcher.group(1);
                currentEntity.setClassName(className);
                currentEntity.setTableName(camelToSnake(className) + "s");
                currentEntity.setColumns(new ArrayList<>());
                currentEntity.setPrimaryKeys(new ArrayList<>());
                currentEntity.setForeignKeys(new ArrayList<>());
                currentEntity.setIndexes(new ArrayList<>());
            } else if (currentEntity != null) {
                Matcher tnMatcher = tablenamePattern.matcher(line);
                if (tnMatcher.find()) {
                    currentEntity.setTableName(tnMatcher.group(1));
                }

                Matcher colMatcher = columnPattern.matcher(line);
                if (colMatcher.find()) {
                    String colName = colMatcher.group(1);
                    String colDetails = colMatcher.group(2);

                    boolean isPk = colDetails.contains("primary_key=True") || colDetails.contains("primary_key = True");
                    boolean isFk = colDetails.contains("ForeignKey");
                    
                    String referencedTable = null;
                    if (isFk) {
                        Matcher fkM = fkPattern.matcher(colDetails);
                        if (fkM.find()) {
                            referencedTable = fkM.group(1);
                        }
                    }

                    DatabaseColumnDto col = DatabaseColumnDto.builder()
                            .columnName(colName)
                            .fieldName(colName)
                            .dataType(colDetails.contains("Integer") ? "Integer" : colDetails.contains("String") ? "String" : "Object")
                            .nullable(!colDetails.contains("nullable=False") && !colDetails.contains("nullable = False"))
                            .primaryKey(isPk)
                            .foreignKey(isFk)
                            .referencedTable(referencedTable)
                            .build();

                    currentEntity.getColumns().add(col);
                    if (isPk) {
                        currentEntity.getPrimaryKeys().add(colName);
                    }
                    if (isFk) {
                        currentEntity.getForeignKeys().add(colName);
                    }
                }
            }
        }

        if (currentEntity != null) {
            entities.add(currentEntity);
        }
    }

    private void parseJsEntities(File file, List<DatabaseEntityDto> entities) throws Exception {
        List<String> lines = Files.readAllLines(file.toPath());
        String name = file.getName();
        String nameWithoutExt = name.substring(0, name.lastIndexOf('.'));

        boolean isSchema = false;
        for (String line : lines) {
            if (line.contains("mongoose.Schema") || line.contains("sequelize.define")) {
                isSchema = true;
                break;
            }
        }

        if (isSchema) {
            DatabaseEntityDto entity = new DatabaseEntityDto();
            entity.setClassName(nameWithoutExt);
            entity.setTableName(camelToSnake(nameWithoutExt) + "s");
            entity.setColumns(new ArrayList<>());
            entity.setPrimaryKeys(new ArrayList<>());
            entity.setForeignKeys(new ArrayList<>());
            entity.setIndexes(new ArrayList<>());

            Pattern fkPattern = Pattern.compile("ref\\s*:\\s*[\"']([^\"']+)[\"']");

            // Look for fields (approximate scanning for MongoDB schema)
            for (String line : lines) {
                line = line.trim();
                if (line.contains(":") && !line.startsWith("const") && !line.startsWith("let") && !line.startsWith("var")) {
                    String[] parts = line.split(":");
                    String fieldName = parts[0].trim();
                    if (fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                        boolean isPk = fieldName.equals("_id") || fieldName.equals("id");
                        boolean isFk = line.contains("ObjectId") && line.contains("ref");

                        String referencedTable = null;
                        if (isFk) {
                            Matcher matcher = fkPattern.matcher(line);
                            if (matcher.find()) {
                                referencedTable = camelToSnake(matcher.group(1)) + "s";
                            }
                        }

                        DatabaseColumnDto col = DatabaseColumnDto.builder()
                                .columnName(fieldName)
                                .fieldName(fieldName)
                                .dataType(line.contains("Number") ? "Number" : line.contains("Date") ? "Date" : "String")
                                .nullable(true)
                                .primaryKey(isPk)
                                .foreignKey(isFk)
                                .referencedTable(referencedTable)
                                .build();

                        entity.getColumns().add(col);
                        if (isPk) entity.getPrimaryKeys().add(fieldName);
                        if (isFk) entity.getForeignKeys().add(fieldName);
                    }
                }
            }

            if (entity.getColumns().isEmpty()) {
                // Default id column if empty scan
                entity.getColumns().add(DatabaseColumnDto.builder()
                        .columnName("id")
                        .fieldName("id")
                        .dataType("String")
                        .nullable(false)
                        .primaryKey(true)
                        .foreignKey(false)
                        .build());
                entity.getPrimaryKeys().add("id");
            }
            entities.add(entity);
        }
    }

    private String generateMermaidEr(List<DatabaseEntityDto> entities) {
        StringBuilder sb = new StringBuilder();
        sb.append("erDiagram\n");

        if (entities.isEmpty()) {
            sb.append("    NO_TABLES ||--|| NO_TABLES : \"empty\"\n");
            return sb.toString();
        }

        // 1. Declare entities and fields
        for (DatabaseEntityDto entity : entities) {
            String cleanName = entity.getTableName().toUpperCase().replace("-", "_");
            sb.append("    ").append(cleanName).append(" {\n");
            for (DatabaseColumnDto col : entity.getColumns()) {
                String keyType = col.getPrimaryKey() ? "PK" : col.getForeignKey() ? "FK" : "";
                sb.append("        ").append(col.getDataType()).append(" ").append(col.getColumnName()).append(" ").append(keyType).append("\n");
            }
            sb.append("    }\n\n");
        }

        // 2. Declare relationships
        Set<String> relationships = new HashSet<>();
        for (DatabaseEntityDto entity : entities) {
            String sourceTable = entity.getTableName().toUpperCase().replace("-", "_");
            for (DatabaseColumnDto col : entity.getColumns()) {
                if (col.getForeignKey() && col.getReferencedTable() != null) {
                    String targetTable = col.getReferencedTable().toUpperCase().replace("-", "_");
                    String relationshipStr = String.format("    %s ||--o{ %s : \"references\"", targetTable, sourceTable);
                    relationships.add(relationshipStr);
                }
            }
        }

        if (relationships.isEmpty()) {
            sb.append("    %% No relationships detected\n");
        } else {
            for (String rel : relationships) {
                sb.append(rel).append("\n");
            }
        }

        return sb.toString();
    }

    private String generateDatabaseSummary(List<DatabaseEntityDto> entities, int relationshipsCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Database Schema Summary\n\n");
        sb.append("Analyzed the application codebase and detected **").append(entities.size()).append("** database tables/entities and **").append(relationshipsCount).append("** schema relationship linkages.\n\n");

        sb.append("## Table Catalog\n\n");
        for (DatabaseEntityDto entity : entities) {
            sb.append("### Table: `").append(entity.getTableName()).append("` (Class: `").append(entity.getClassName()).append("`)\n");
            sb.append("* **Primary Key(s)**: ").append(String.join(", ", entity.getPrimaryKeys())).append("\n");
            if (!entity.getIndexes().isEmpty()) {
                sb.append("* **Index(es)**: `").append(String.join("`, `", entity.getIndexes())).append("`\n");
            }
            sb.append("\n| Column Name | Data Type | Key Constraints | Nullable |\n");
            sb.append("|---|---|---|---|\n");
            for (DatabaseColumnDto col : entity.getColumns()) {
                String constraint = col.getPrimaryKey() ? "PRIMARY KEY" : col.getForeignKey() ? "FOREIGN KEY (" + col.getReferencedTable() + ")" : "-";
                sb.append("| `").append(col.getColumnName()).append("` | ").append(col.getDataType()).append(" | ").append(constraint).append(" | ").append(col.getNullable() ? "YES" : "NO").append(" |\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String camelToSnake(String str) {
        if (str == null) return "";
        return str.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
