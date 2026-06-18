package com.example.smartkb.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatStreamRequest {

    @Positive(message = "conversationId 必须为正数")
    private Long conversationId;

    @NotNull(message = "kbId 不能为空")
    @Positive(message = "kbId 必须为正数")
    private Long kbId;

    @NotBlank(message = "question 不能为空")
    @Size(max = 4000, message = "question 不能超过 4000 个字符")
    private String question;
}

