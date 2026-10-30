package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.AnalysisRecord;
import java.io.File;

public interface SourceCodeAnalyzerService {
    /**
     * Executes the source code metrics parser over a local repository checkout,
     * collecting class structure details, fields, methods, inheritance loops,
     * cyclomatic complexity metrics, and saves results.
     *
     * @param record        the AnalysisRecord parent status object
     * @param repositoryDir the local directory of the cloned repository code
     */
    void analyze(AnalysisRecord record, File repositoryDir);
}
