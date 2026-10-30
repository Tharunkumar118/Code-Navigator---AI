package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.AnalysisRecord;
import java.io.File;

public interface SecurityAnalyzerService {
    void analyze(AnalysisRecord record, File repositoryDir);
}
