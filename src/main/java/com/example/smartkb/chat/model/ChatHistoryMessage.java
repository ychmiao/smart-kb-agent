package com.example.smartkb.chat.model;

/**
 * 聊天历史消息 —— 用于在 Redis 和 Prompt 中传递对话上下文。
 *
 * @param role    角色（"user" 或 "assistant"）
 * @param content 消息内容
 */
public record ChatHistoryMessage(String role, String content) {
}

