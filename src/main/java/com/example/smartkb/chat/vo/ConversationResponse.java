package com.example.smartkb.chat.vo;

import java.time.LocalDateTime;

public record ConversationResponse(
        Long id,
        Long kbId,
        String title,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {
}
