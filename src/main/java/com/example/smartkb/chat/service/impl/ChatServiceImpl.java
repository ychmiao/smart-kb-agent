package com.example.smartkb.chat.service.impl;

import com.example.smartkb.chat.dto.ChatStreamRequest;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.model.ChatHistoryMessage;
import com.example.smartkb.chat.model.DoneSseEvent;
import com.example.smartkb.chat.model.RewriteSseEvent;
import com.example.smartkb.chat.model.SourceReference;
import com.example.smartkb.chat.model.SourcesSseEvent;
import com.example.smartkb.chat.model.TokenSseEvent;
import com.example.smartkb.chat.service.ChatHistoryService;
import com.example.smartkb.chat.service.ChatPersistenceService;
import com.example.smartkb.chat.service.ChatService;
import com.example.smartkb.chat.service.ConversationService;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.service.RetrievalService;
import com.example.smartkb.search.vo.RetrievedChunk;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private static final int RETRIEVAL_TOP_K = 5;

    private final ConversationService conversationService;
    private final ChatHistoryService chatHistoryService;
    private final RetrievalService retrievalService;
    private final LlmGatewayService llmGatewayService;
    private final ChatPersistenceService chatPersistenceService;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(ConversationService conversationService,
                           ChatHistoryService chatHistoryService,
                           RetrievalService retrievalService,
                           LlmGatewayService llmGatewayService,
                           ChatPersistenceService chatPersistenceService,
                           ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.chatHistoryService = chatHistoryService;
        this.retrievalService = retrievalService;
        this.llmGatewayService = llmGatewayService;
        this.chatPersistenceService = chatPersistenceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(ChatStreamRequest request) {
        Long userId = UserContext.requireUserId();
        String question = request.getQuestion().strip();
        Conversation conversation = conversationService.getOrCreate(
                request.getConversationId(),
                request.getKbId(),
                userId,
                question
        );
        List<ChatHistoryMessage> history = chatHistoryService.getRecentHistory(conversation.getId());
        ServerSentEvent<String> rewriteEvent = sse(new RewriteSseEvent(question));

        Flux<ServerSentEvent<String>> answerStream = Mono.fromCallable(
                        () -> retrievalService.retrieve(request.getKbId(), question, RETRIEVAL_TOP_K)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(chunks -> streamAnswer(conversation, question, history, chunks));
        return Flux.concat(Flux.just(rewriteEvent), answerStream);
    }

    private Flux<ServerSentEvent<String>> streamAnswer(Conversation conversation, String question,
                                                        List<ChatHistoryMessage> history,
                                                        List<RetrievedChunk> chunks) {
        String prompt = buildRagPrompt(question, history, chunks);
        String requestId = "chat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        StringBuilder answer = new StringBuilder();
        List<SourceReference> sources = chunks.stream()
                .map(chunk -> new SourceReference(
                        chunk.getDocId(),
                        chunk.getFileName(),
                        chunk.getSourceText()
                ))
                .toList();

        Flux<ServerSentEvent<String>> tokenEvents = llmGatewayService.streamChat(requestId, prompt)
                .doOnNext(answer::append)
                .map(token -> sse(new TokenSseEvent(token)));
        Flux<ServerSentEvent<String>> terminalEvents = Flux.defer(() -> {
            submitPersistence(conversation.getId(), question, answer.toString(), sources);
            return Flux.just(
                    sse(new SourcesSseEvent(sources)),
                    sse(new DoneSseEvent())
            );
        });
        return tokenEvents.concatWith(terminalEvents);
    }

    private void submitPersistence(Long conversationId, String question, String answer,
                                   List<SourceReference> sources) {
        try {
            chatPersistenceService.persistExchange(
                    conversationId,
                    question,
                    answer,
                    question,
                    sources
            );
        } catch (RuntimeException exception) {
            log.error("Failed to submit chat persistence task: conversationId={}",
                    conversationId, exception);
        }
    }

    private String buildRagPrompt(String question, List<ChatHistoryMessage> history,
                                  List<RetrievedChunk> chunks) {
        StringBuilder chunkText = new StringBuilder();
        for (RetrievedChunk chunk : chunks) {
            chunkText.append("【文档：")
                    .append(chunk.getFileName())
                    .append("】\n")
                    .append(chunk.getContent())
                    .append("\n\n");
        }

        StringBuilder historyText = new StringBuilder();
        for (ChatHistoryMessage message : history) {
            historyText.append(message.role())
                    .append("：")
                    .append(message.content())
                    .append('\n');
        }

        return """
                你是一个专业的知识库问答助手。
                请严格基于下面的参考文档回答用户问题。
                如果参考文档中没有相关信息，请回答：
                “根据现有知识库，无法找到相关信息。”
                不要编造内容。

                参考文档：
                %s
                历史对话：
                %s
                用户问题：
                %s

                请在回答末尾用【参考来源：文档名】标明引用来源。
                """.formatted(chunkText, historyText, question);
    }

    private ServerSentEvent<String> sse(Object event) {
        try {
            return ServerSentEvent.builder(objectMapper.writeValueAsString(event)).build();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("SSE 事件序列化失败", exception);
        }
    }
}
