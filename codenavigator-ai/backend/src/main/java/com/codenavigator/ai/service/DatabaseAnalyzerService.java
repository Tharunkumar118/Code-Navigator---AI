package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.AnalysisRecord;
import java.io.File;

public interface DatabaseAnalyzerService {
    void analyze(AnalysisRecord record, File repositoryDir);
}
