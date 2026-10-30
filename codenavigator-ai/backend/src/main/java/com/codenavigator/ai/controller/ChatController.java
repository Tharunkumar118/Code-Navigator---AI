package com.codenavigator.ai.controller;

import com.codenavigator.ai.dto.ApiResponse;
import com.codenavigator.ai.dto.ChatRequestDto;
import com.codenavigator.ai.dto.ChatResponseDto;
import com.codenavigator.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/analyses/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponseDto>> processChat(
            @RequestBody ChatRequestDto request,
            Principal principal) {
        ChatResponseDto response = chatService.processChat(request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("AI response compiled successfully", response));
    }
}
