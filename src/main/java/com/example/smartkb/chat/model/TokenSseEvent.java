package com.example.smartkb.chat.model;

/**
 * SSE token 事件 —— AI 回答的一个片段。
 * <p>
 * type: "token"<br>
 * content: 回答文本片段（多个 token 事件拼接为完整回答）
 */
public record TokenSseEvent(String type, String content) {

    public TokenSseEvent(String content) {
        this("token", content);
    }
}

