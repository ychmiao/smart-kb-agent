package com.example.smartkb.chat.service;

import com.example.smartkb.chat.entity.ChatMessage;
import com.example.smartkb.chat.mapper.ChatMessageMapper;
import com.example.smartkb.chat.model.ChatHistoryMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话历史缓存服务 —— 使用 Redis 存储最近 N 条对话消息。
 * <p>
 * Key 格式：{@code chat:history:{conversationId}}，Value 为 JSON 序列化的消息列表。
 * 读取时优先查 Redis，未命中或 Redis 异常时回源 MySQL（{@code kb_message} 表）。
 * 写入时追加新消息并裁剪至最近 10 条，设置 7 天 TTL。
 */
@Slf4j
@Service
public class ChatHistoryService {

    /** Redis Key 前缀 */
    private static final String HISTORY_KEY_PREFIX = "chat:history:";
    /** 保留的最大历史消息数 */
    private static final int MAX_HISTORY_MESSAGES = 10;
    /** Redis 过期时间 */
    private static final Duration HISTORY_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ChatMessageMapper chatMessageMapper;

    public ChatHistoryService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                              ChatMessageMapper chatMessageMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.chatMessageMapper = chatMessageMapper;
    }

    public List<ChatHistoryMessage> getRecentHistory(Long conversationId) {
        String key = historyKey(conversationId);
        try {
            List<String> values = redisTemplate.opsForList().range(key, 0, -1);
            if (values != null && !values.isEmpty()) {
                List<ChatHistoryMessage> history = new ArrayList<>(values.size());
                for (String value : values) {
                    history.add(objectMapper.readValue(value, ChatHistoryMessage.class));
                }
                return history;
            }
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Failed to read chat history from Redis, falling back to MySQL: conversationId={}",
                    conversationId, exception);
        }
        return loadFromDatabase(conversationId);
    }

    public void appendExchange(Long conversationId, String question, String answer) {
        String key = historyKey(conversationId);
        try {
            String userMessage = objectMapper.writeValueAsString(new ChatHistoryMessage("user", question));
            String assistantMessage = objectMapper.writeValueAsString(
                    new ChatHistoryMessage("assistant", answer)
            );
            redisTemplate.opsForList().rightPushAll(key, userMessage, assistantMessage);
            redisTemplate.opsForList().trim(key, -MAX_HISTORY_MESSAGES, -1);
            redisTemplate.expire(key, HISTORY_TTL);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("Failed to update chat history in Redis: conversationId={}", conversationId, exception);
        }
    }

    private List<ChatHistoryMessage> loadFromDatabase(Long conversationId) {
        List<ChatMessage> messages = chatMessageMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatMessage>()
                        .eq(ChatMessage::getConversationId, conversationId)
                        .orderByDesc(ChatMessage::getCreateTime)
                        .last("LIMIT " + MAX_HISTORY_MESSAGES)
        );
        Collections.reverse(messages);
        return messages.stream()
                .map(message -> new ChatHistoryMessage(message.getRole(), message.getContent()))
                .toList();
    }

    private String historyKey(Long conversationId) {
        return HISTORY_KEY_PREFIX + conversationId;
    }
}

