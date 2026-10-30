package com.codenavigator.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ActivityLogDto {
    private String userEmail;
    private String action;
    private String description;
    private Instant createdAt;
}
