package com.codenavigator.ai.service;

import com.codenavigator.ai.entity.AnalysisRecord;
import java.io.File;

public interface DiagramGeneratorService {
    void generateDiagrams(AnalysisRecord record, File repositoryDir);
}
