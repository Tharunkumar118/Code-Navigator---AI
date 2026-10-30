package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.ApiResponse;
import com.codenavigator.ai.dto.RepositoryRequest;
import com.codenavigator.ai.dto.RepositoryResponse;
import com.codenavigator.ai.service.RepositoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final RepositoryService repositoryService;

    @PostMapping
    public ResponseEntity<ApiResponse<RepositoryResponse>> submitRepository(
            @Valid @RequestBody RepositoryRequest request,
            Principal principal) {
        RepositoryResponse response = repositoryService.submitRepository(request, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Repository submitted for cloning and analysis", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RepositoryResponse>> getRepository(
            @PathVariable Long id,
            Principal principal) {
        RepositoryResponse response = repositoryService.getRepository(id, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Repository details retrieved successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<RepositoryResponse>>> getRepositories(Principal principal) {
        List<RepositoryResponse> response = repositoryService.getRepositories(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Repositories retrieved successfully", response));
    }
}
