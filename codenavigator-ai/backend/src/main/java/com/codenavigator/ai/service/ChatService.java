package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.ChatRequestDto;
import com.codenavigator.ai.dto.ChatResponseDto;

public interface ChatService {
    ChatResponseDto processChat(ChatRequestDto request, String email);
}
