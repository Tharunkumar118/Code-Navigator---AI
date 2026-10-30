package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.CodeRepository;
import java.io.File;

public interface ProjectScannerService {
    /**
     * Scans the cloned repository directory recursively, detects build tools,
     * modules, packages, and code components, and stores them in the entity.
     *
     * @param repo    the CodeRepository entity database record to populate
     * @param rootDir the local root directory of the cloned repository
     */
    void scanRepository(CodeRepository repo, File rootDir);
}
