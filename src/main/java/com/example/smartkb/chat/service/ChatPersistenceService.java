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
                                List<SourceReference> sources) {
        try {
            String sourceJson = objectMapper.writeValueAsString(sources);
            transactionTemplate.executeWithoutResult(status -> {
                ChatMessage userMessage = createMessage(
                        conversationId,
                        "user",
                        question,
                        null,
                        rewrittenQuery,
                        needRetrieval ? 1 : 0
                );
                ChatMessage assistantMessage = createMessage(
                        conversationId,
                        "assistant",
                        answer,
                        sourceJson,
                        rewrittenQuery,
                        needRetrieval ? 1 : 0
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

    private ChatMessage createMessage(Long conversationId, String role, String content,
                                      String sourceDocs, String rewrittenQuery, int needRetrieval) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setSourceDocs(sourceDocs);
        message.setRewrittenQuery(rewrittenQuery);
        message.setNeedRetrieval(needRetrieval);
        return message;
    }
}
