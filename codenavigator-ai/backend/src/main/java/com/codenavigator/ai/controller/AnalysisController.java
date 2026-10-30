package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.AnalysisRequest;
import com.codenavigator.ai.dto.AnalysisResponse;
import com.codenavigator.ai.dto.ApiResponse;
import com.codenavigator.ai.service.AnalysisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/analyses")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping
    public ResponseEntity<ApiResponse<AnalysisResponse>> submitAnalysis(
            @Valid @RequestBody AnalysisRequest request,
            Principal principal) {
        AnalysisResponse response = analysisService.submitAnalysis(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Analysis job submitted successfully", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AnalysisResponse>> getAnalysis(
            @PathVariable Long id,
            Principal principal) {
        AnalysisResponse response = analysisService.getAnalysis(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Analysis details retrieved successfully", response));
    }

    @GetMapping("/history/{repositoryId}")
    public ResponseEntity<ApiResponse<List<AnalysisResponse>>> getAnalysisHistory(
            @PathVariable Long repositoryId,
            Principal principal) {
        List<AnalysisResponse> response = analysisService.getAnalysisHistory(repositoryId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Analysis history retrieved successfully", response));
    }
}
