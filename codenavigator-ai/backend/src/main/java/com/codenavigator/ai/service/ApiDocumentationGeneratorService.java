package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.AnalysisRecord;
import java.io.File;

public interface ApiDocumentationGeneratorService {
    /**
     * Executes the API parsing routine over a local repository directory,
     * identifying REST controllers, endpoints, mapping annotations, and
     * parameters, and generates Swagger/OpenAPI 3.0 specification documents.
     *
     * @param record        the AnalysisRecord parent status object
     * @param repositoryDir the local directory of the cloned repository code
     */
    void analyze(AnalysisRecord record, File repositoryDir);
}
