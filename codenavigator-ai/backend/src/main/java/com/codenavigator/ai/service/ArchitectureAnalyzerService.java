package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.AnalysisRecord;
import java.io.File;

public interface ArchitectureAnalyzerService {
    /**
     * Executes the architectural parser over a local repository checkout,
     * building dependency, module, and call graphs, and saves results.
     *
     * @param record        the AnalysisRecord parent status object
     * @param repositoryDir the local directory of the cloned repository code
     */
    void analyze(AnalysisRecord record, File repositoryDir);
}
