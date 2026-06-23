package com.example.smartkb.chat.vo;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        String role,
        String content,
        String rewrittenQuery,
        Boolean needRetrieval,
        String llmProvider,
        LocalDateTime createTime
) {
}
