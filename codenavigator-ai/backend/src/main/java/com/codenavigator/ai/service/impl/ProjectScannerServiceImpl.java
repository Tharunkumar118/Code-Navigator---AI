package com.codenavigator.ai.service.impl;

import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.service.ProjectScannerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectScannerServiceImpl implements ProjectScannerService {

    private final ObjectMapper objectMapper;

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-zA-Z0-9_.]+)\\s*;");
    private static final Pattern CLASS_PATTERN = Pattern.compile("^\\s*(?:public|protected|private|abstract|static|final|strictfp|\\s)*\\s*(?:class|interface|enum|@interface|record)\\s+([a-zA-Z0-9_]+)");

    @Override
    public void scanRepository(CodeRepository repo, File rootDir) {
        log.info("Deep scanning repository: {} at directory: {}", repo.getRepoName(), rootDir.getAbsolutePath());

        // 1. Detect Build Tool
        repo.setBuildTool(detectBuildTool(rootDir));

        // Scan state lists
        Set<String> modules = new TreeSet<>();
        List<String> configFiles = new ArrayList<>();
        List<String> controllers = new ArrayList<>();
        List<String> services = new ArrayList<>();
        List<String> repositories = new ArrayList<>();
        List<String> entities = new ArrayList<>();
        List<String> utilities = new ArrayList<>();

        // 2. Traverse directory recursively
        scanDirectory(rootDir, rootDir, modules, configFiles, controllers, services, repositories, entities, utilities);

        // 3. Serialize and save to repository entity
        try {
            repo.setModules(objectMapper.writeValueAsString(new ArrayList<>(modules)));
            repo.setConfigurationFiles(objectMapper.writeValueAsString(configFiles));
            repo.setControllers(objectMapper.writeValueAsString(controllers));
            repo.setServices(objectMapper.writeValueAsString(services));
            repo.setRepositories(objectMapper.writeValueAsString(repositories));
            repo.setEntities(objectMapper.writeValueAsString(entities));
            repo.setUtilities(objectMapper.writeValueAsString(utilities));
            
            log.info("Deep scan completed for: {}. Found {} controllers, {} services, {} repositories, {} entities, {} utilities.",
                    repo.getRepoName(), controllers.size(), services.size(), repositories.size(), entities.size(), utilities.size());
        } catch (Exception e) {
            log.error("Failed to serialize scan results for repo: {}", repo.getRepoName(), e);
        }
    }

    private String detectBuildTool(File rootDir) {
        if (new File(rootDir, "pom.xml").exists()) {
            return "Maven";
        }
        if (new File(rootDir, "build.gradle").exists() || new File(rootDir, "build.gradle.kts").exists()) {
            return "Gradle";
        }
        if (new File(rootDir, "package.json").exists()) {
            return "NPM";
        }
        if (new File(rootDir, "requirements.txt").exists() || new File(rootDir, "pyproject.toml").exists() || new File(rootDir, "setup.py").exists()) {
            return "Pip/Poetry";
        }
        return "Unknown Build Tool";
    }

    private void scanDirectory(File rootDir, File currentDir, Set<String> modules, List<String> configFiles,
                               List<String> controllers, List<String> services, List<String> repositories,
                               List<String> entities, List<String> utilities) {
        File[] files = currentDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            // Ignore hidden files and dependency/build output folders
            if (name.startsWith(".") || name.equals("node_modules") || name.equals("target") || name.equals("build")) {
                continue;
            }

            if (file.isDirectory()) {
                scanDirectory(rootDir, file, modules, configFiles, controllers, services, repositories, entities, utilities);
            } else {
                String relativePath = file.getAbsolutePath().substring(rootDir.getAbsolutePath().length() + 1).replace('\\', '/');

                // Check for Config Files
                if (isConfigFile(file)) {
                    configFiles.add(relativePath);
                }

                // Scan source code files depending on language
                if (name.endsWith(".java")) {
                    classifyJavaFile(file, modules, controllers, services, repositories, entities, utilities);
                } else if (name.endsWith(".py")) {
                    classifyPythonFile(file, modules, controllers, services, repositories, entities, utilities);
                } else if (name.endsWith(".js") || name.endsWith(".ts")) {
                    classifyJsFile(file, modules, controllers, services, repositories, entities, utilities);
                } else if (name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".cc") || name.endsWith(".h") || name.endsWith(".hpp")) {
                    classifyCppFile(file, modules, controllers, services, repositories, entities, utilities);
                }
            }
        }
    }

    private boolean isConfigFile(File file) {
        String name = file.getName().toLowerCase();
        // Standard config extensions and filenames
        return name.endsWith(".properties") || 
               name.endsWith(".yml") || 
               name.endsWith(".yaml") || 
               name.endsWith(".env") || 
               name.endsWith("dockerfile") || 
               name.startsWith("docker-compose") || 
               (name.endsWith(".json") && (name.equals("package.json") || name.equals("tsconfig.json") || name.equals("composer.json"))) || 
               (name.endsWith(".xml") && (name.equals("pom.xml") || name.equals("web.xml") || name.equals("logback.xml")));
    }

    private void classifyJavaFile(File file, Set<String> modules, List<String> controllers, List<String> services,
                                  List<String> repositories, List<String> entities, List<String> utilities) {
        try {
            List<String> lines = Files.readAllLines(file.toPath());
            String content = String.join("\n", lines);

            String packageName = "";
            String className = "";

            // Parse package name and class name
            for (String line : lines) {
                line = line.trim();
                if (packageName.isEmpty()) {
                    Matcher pkgMatcher = PACKAGE_PATTERN.matcher(line);
                    if (pkgMatcher.find()) {
                        packageName = pkgMatcher.group(1);
                        modules.add(packageName);
                    }
                }
                if (className.isEmpty()) {
                    Matcher clsMatcher = CLASS_PATTERN.matcher(line);
                    if (clsMatcher.find()) {
                        className = clsMatcher.group(1);
                    }
                }
                if (!packageName.isEmpty() && !className.isEmpty()) {
                    break;
                }
            }

            if (className.isEmpty()) {
                // Fallback class name extraction from file name
                String filename = file.getName();
                className = filename.substring(0, filename.length() - 5);
            }

            String fullyQualifiedName = packageName.isEmpty() ? className : packageName + "." + className;

            // Classify based on Spring/JPA annotations
            boolean classified = false;

            if (content.contains("@RestController") || content.contains("@Controller")) {
                controllers.add(fullyQualifiedName);
                classified = true;
            }
            if (content.contains("@Service")) {
                services.add(fullyQualifiedName);
                classified = true;
            }
            if (content.contains("@Repository") || content.contains("extends JpaRepository") || content.contains("extends CrudRepository")) {
                repositories.add(fullyQualifiedName);
                classified = true;
            }
            if (content.contains("@Entity") || content.contains("@Table") || content.contains("@MappedSuperclass")) {
                entities.add(fullyQualifiedName);
                classified = true;
            }
            
            // Check for Config or Helper utilities
            if (!classified) {
                if (content.contains("@Configuration") || 
                    className.endsWith("Util") || 
                    className.endsWith("Utils") || 
                    className.endsWith("Helper") || 
                    className.endsWith("Factory") || 
                    className.endsWith("Builder") || 
                    className.endsWith("Holder")) {
                    utilities.add(fullyQualifiedName);
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse Java file: {} for classification. Error: {}", file.getAbsolutePath(), e.getMessage());
        }
    }

    private void classifyPythonFile(File file, Set<String> modules, List<String> controllers, List<String> services,
                                     List<String> repositories, List<String> entities, List<String> utilities) {
        String name = file.getName();
        String path = file.getPath().replace('\\', '/');
        String nameWithoutExt = name.endsWith(".py") ? name.substring(0, name.length() - 3) : name;

        String relativePath = getRelativeModuleName(path, nameWithoutExt);
        modules.add(getRelativeParentPath(relativePath));

        try {
            String content = Files.readString(file.toPath());
            boolean classified = false;

            if (content.contains("FastAPI") || content.contains("Flask") || content.contains("@app.") || name.contains("controller") || name.contains("routes") || name.contains("views")) {
                controllers.add(relativePath);
                classified = true;
            }
            if (name.contains("service") || name.contains("manager") || name.contains("logic")) {
                services.add(relativePath);
                classified = true;
            }
            if (content.contains("declarative_base()") || content.contains("db.Model") || name.contains("model") || name.contains("entity")) {
                entities.add(relativePath);
                classified = true;
            }
            if (name.contains("repo") || name.contains("dao")) {
                repositories.add(relativePath);
                classified = true;
            }
            if (!classified && (name.contains("util") || name.contains("helper") || name.contains("tool") || name.contains("common"))) {
                utilities.add(relativePath);
            }
        } catch (Exception e) {
            log.error("Failed to classify python file: {}", file.getName(), e);
        }
    }

    private void classifyJsFile(File file, Set<String> modules, List<String> controllers, List<String> services,
                                 List<String> repositories, List<String> entities, List<String> utilities) {
        String name = file.getName();
        String path = file.getPath().replace('\\', '/');
        String nameWithoutExt = name.substring(0, name.lastIndexOf('.'));

        String relativePath = getRelativeModuleName(path, nameWithoutExt);
        modules.add(getRelativeParentPath(relativePath));

        try {
            String content = Files.readString(file.toPath());
            boolean classified = false;

            if (content.contains("express") || content.contains("router") || name.contains("controller") || name.contains("routes") || name.contains("api")) {
                controllers.add(relativePath);
                classified = true;
            }
            if (name.contains("service") || name.contains("logic") || name.contains("handler")) {
                services.add(relativePath);
                classified = true;
            }
            if (content.contains("mongoose.model") || content.contains("sequelize.define") || name.contains("model") || name.contains("entity") || name.contains("schema")) {
                entities.add(relativePath);
                classified = true;
            }
            if (name.contains("repository") || name.contains("dao")) {
                repositories.add(relativePath);
                classified = true;
            }
            if (!classified && (name.contains("util") || name.contains("helper") || name.contains("config") || name.contains("tool"))) {
                utilities.add(relativePath);
            }
        } catch (Exception e) {
            log.error("Failed to classify JS file: {}", file.getName(), e);
        }
    }

    private void classifyCppFile(File file, Set<String> modules, List<String> controllers, List<String> services,
                                  List<String> repositories, List<String> entities, List<String> utilities) {
        String name = file.getName();
        String path = file.getPath().replace('\\', '/');
        String nameWithoutExt = name.substring(0, name.lastIndexOf('.'));

        String relativePath = getRelativeModuleName(path, nameWithoutExt);
        modules.add(getRelativeParentPath(relativePath));

        try {
            String content = Files.readString(file.toPath());
            boolean classified = false;

            if (content.contains("int main(") || content.contains("void main(")) {
                controllers.add(relativePath);
                classified = true;
            }
            if (name.endsWith(".cpp") || name.endsWith(".c") || name.endsWith(".cc")) {
                services.add(relativePath);
                classified = true;
            }
            if (name.endsWith(".h") || name.endsWith(".hpp")) {
                utilities.add(relativePath);
            }
        } catch (Exception e) {
            log.error("Failed to classify C/C++ file: {}", file.getName(), e);
        }
    }

    private String getRelativeModuleName(String path, String name) {
        int idx = path.indexOf("/src/");
        if (idx != -1) {
            return path.substring(idx + 1);
        }
        idx = path.lastIndexOf('/');
        if (idx != -1) {
            int secondLast = path.lastIndexOf('/', idx - 1);
            if (secondLast != -1) {
                return path.substring(secondLast + 1);
            }
            return path.substring(idx + 1);
        }
        return name;
    }

    private String getRelativeParentPath(String path) {
        int idx = path.lastIndexOf('/');
        if (idx != -1) {
            return path.substring(0, idx);
        }
        return "";
    }
}
