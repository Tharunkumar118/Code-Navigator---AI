package com.codenavigator.ai.service;

import com.codenavigator.ai.dto.ChatRequestDto;
import com.codenavigator.ai.dto.ChatResponseDto;
import com.codenavigator.ai.entity.CodeRepository;
import com.codenavigator.ai.repository.AnalysisRecordRepository;
import com.codenavigator.ai.repository.CodeRepositoryRepository;
import com.codenavigator.ai.service.impl.ChatServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ChatServiceTest {

    @Mock
    private CodeRepositoryRepository repositoryRepository;

    @Mock
    private AnalysisRecordRepository analysisRecordRepository;

    @InjectMocks
    private ChatServiceImpl chatService;

    private CodeRepository mockRepo;

    @BeforeEach
    void setUp() {
        mockRepo = new CodeRepository();
        mockRepo.setId(1L);
        mockRepo.setRepoName("Test-Chat-Repo");
        mockRepo.setPrimaryLanguage("Java");
        mockRepo.setBuildTool("Maven");
    }

    @Test
    void testProcessChatOfflineSummaryFallback() {
        when(repositoryRepository.findById(1L)).thenReturn(Optional.of(mockRepo));
        when(analysisRecordRepository.findByRepositoryIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());

        ChatRequestDto request = ChatRequestDto.builder()
                .repositoryId(1L)
                .prompt("Give me a summary of this project")
                .build();

        ChatResponseDto response = chatService.processChat(request, "developer@example.com");

        assertNotNull(response);
        assertEquals("OFFLINE", response.getMode());
        assertTrue(response.getMessage().contains("Test-Chat-Repo"));
        assertTrue(response.getMessage().contains("No API scan records found."));
    }

    @Test
    void testProcessChatOfflineApiTrigger() {
        when(repositoryRepository.findById(1L)).thenReturn(Optional.of(mockRepo));
        when(analysisRecordRepository.findByRepositoryIdOrderByCreatedAtDesc(1L)).thenReturn(Collections.emptyList());

        ChatRequestDto request = ChatRequestDto.builder()
                .repositoryId(1L)
                .prompt("Show me the API routes")
                .build();

        ChatResponseDto response = chatService.processChat(request, "developer@example.com");

        assertNotNull(response);
        assertEquals("OFFLINE", response.getMode());
        assertTrue(response.getMessage().contains("API Route Directory"));
    }
}
