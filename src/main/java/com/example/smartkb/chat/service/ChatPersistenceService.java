package com.example.smartkb.chat.service;

import com.example.smartkb.chat.entity.ChatMessage;
import com.example.smartkb.chat.mapper.ChatMessageMapper;
import com.example.smartkb.chat.model.SourceReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * 会话消息持久化服务 —— 异步保存问答对到 MySQL，并更新 Redis 历史缓存。
 * <p>
 * 使用 TransactionTemplate 保证用户问题 + AI 回答在同一个事务中写入。
 * 持久化后在独立步骤中更新 Redis 列表（即使失败也不影响数据库一致性）。
 * 使用独立线程池 {@code chatPersistenceTaskExecutor}，不阻塞 SSE 流式响应。
 */
@Slf4j
@Service
public class ChatPersistenceService {

    private final ChatMessageMapper chatMessageMapper;
    private final ChatHistoryService chatHistoryService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public ChatPersistenceService(ChatMessageMapper chatMessageMapper,
                                  ChatHistoryService chatHistoryService,
                                  ObjectMapper objectMapper,
                                  TransactionTemplate transactionTemplate) {
        this.chatMessageMapper = chatMessageMapper;
        this.chatHistoryService = chatHistoryService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Async("chatPersistenceTaskExecutor")
    public void persistExchange(Long conversationId, String question, String answer,
                                String rewrittenQuery, boolean needRetrieval,
                                List<SourceReference> sources,
                                String llmProvider, Integer estimatedTokens) {
        try {
            String sourceJson = objectMapper.writeValueAsString(sources);
            transactionTemplate.executeWithoutResult(status -> {
                ChatMessage userMessage = createMessage(
                        conversationId,
                        "user",
                        question,
                        null,
                        rewrittenQuery,
                        needRetrieval ? 1 : 0,
                        null,
                        null
                );
                ChatMessage assistantMessage = createMessage(
                        conversationId,
                        "assistant",
                        answer,
                        sourceJson,
                        rewrittenQuery,
                        needRetrieval ? 1 : 0,
                        llmProvider,
                        estimatedTokens
                );
                if (chatMessageMapper.insert(userMessage) != 1
                        || chatMessageMapper.insert(assistantMessage) != 1) {
                    throw new IllegalStateException("保存会话消息失败");
                }
            });
            chatHistoryService.appendExchange(conversationId, question, answer);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.error("Failed to persist chat exchange: conversationId={}", conversationId, exception);
        }
    }

    public void persistExchange(Long conversationId, String question, String answer,
                                String rewrittenQuery, boolean needRetrieval,
                                List<SourceReference> sources) {
        persistExchange(conversationId, question, answer, rewrittenQuery,
                needRetrieval, sources, null, null);
    }

    private ChatMessage createMessage(Long conversationId, String role, String content,
                                      String sourceDocs, String rewrittenQuery, int needRetrieval,
                                      String llmProvider, Integer tokenCount) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setSourceDocs(sourceDocs);
        message.setRewrittenQuery(rewrittenQuery);
        message.setNeedRetrieval(needRetrieval);
        message.setLlmProvider(llmProvider);
        message.setTokenCount(tokenCount);
        return message;
    }
}
