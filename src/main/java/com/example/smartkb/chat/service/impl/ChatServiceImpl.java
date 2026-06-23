package com.example.smartkb.chat.service.impl;

import com.example.smartkb.chat.dto.ChatStreamRequest;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.model.ChatHistoryMessage;
import com.example.smartkb.chat.model.DoneSseEvent;
import com.example.smartkb.chat.model.ErrorSseEvent;
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
import com.example.smartkb.llm.exception.AllLlmProviderFailedException;
import com.example.smartkb.search.service.RetrievalService;
import com.example.smartkb.search.model.QueryRewriteResult;
import com.example.smartkb.search.service.QueryRewriteService;
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

/**
 * Chat 服务实现 —— SSE 流式问答的主编排器。
 * <p>
 * 调用链：
 * <ol>
 *   <li>获取/创建会话 + 校验归属</li>
 *   <li>从 Redis（或 MySQL）获取最近 10 条历史</li>
 *   <li>调用 QueryRewriteService 做意图识别和查询重写</li>
 *   <li>返回 rewrite SSE 事件</li>
 *   <li>若 needRetrieval=true，调用 RetrievalService 检索 Top5 文档片段</li>
 *   <li>构造 RAG/闲聊 Prompt，调用 LlmGatewayService.streamChat()</li>
 *   <li>流式返回 token 事件 → sources 事件 → done 事件</li>
 *   <li>异步持久化问答对到 MySQL 并更新 Redis 历史</li>
 * </ol>
 * 全部异常通过 {@code onErrorResume} 统一转为 error + done，保证连接正常结束。
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    /** 检索时返回的 TopK 文档片段数 */
    private static final int RETRIEVAL_TOP_K = 5;

    private final ConversationService conversationService;
    private final ChatHistoryService chatHistoryService;
    private final QueryRewriteService queryRewriteService;
    private final RetrievalService retrievalService;
    private final LlmGatewayService llmGatewayService;
    private final ChatPersistenceService chatPersistenceService;
    private final ObjectMapper objectMapper;

    public ChatServiceImpl(ConversationService conversationService,
                           ChatHistoryService chatHistoryService,
                           QueryRewriteService queryRewriteService,
                           RetrievalService retrievalService,
                           LlmGatewayService llmGatewayService,
                           ChatPersistenceService chatPersistenceService,
                           ObjectMapper objectMapper) {
        this.conversationService = conversationService;
        this.chatHistoryService = chatHistoryService;
        this.queryRewriteService = queryRewriteService;
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
        Flux<ServerSentEvent<String>> stream = Mono.fromCallable(
                        () -> queryRewriteService.rewrite(question, history)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(rewriteResult -> Flux.concat(
                        Flux.just(sse(new RewriteSseEvent(
                                rewriteResult.getNeedRetrieval(),
                                rewriteResult.getRewrittenQuery()
                        ))),
                        routeAnswer(
                                userId,
                                conversation,
                                request.getKbId(),
                                question,
                                history,
                                rewriteResult
                        )
                ));
        return stream.onErrorResume(exception -> {
            String message = resolveErrorMessage(exception);
            log.error("SSE stream error: conversationId={}, kbId={}",
                    conversation.getId(), request.getKbId(), exception);
            return Flux.just(
                    sse(new ErrorSseEvent(message)),
                    sse(new DoneSseEvent())
            );
        });
    }

    private Flux<ServerSentEvent<String>> routeAnswer(Long userId, Conversation conversation, Long kbId,
                                                       String question, List<ChatHistoryMessage> history,
                                                       QueryRewriteResult rewriteResult) {
        if (!rewriteResult.getNeedRetrieval()) {
            return streamAnswer(
                    conversation,
                    question,
                    history,
                    List.of(),
                    rewriteResult
            );
        }
        return Mono.fromCallable(() -> {
                    UserContext.setUserId(userId);
                    return retrievalService.retrieve(
                            kbId,
                            rewriteResult.getRewrittenQuery(),
                            RETRIEVAL_TOP_K
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(chunks -> streamAnswer(
                        conversation,
                        question,
                        history,
                        chunks,
                        rewriteResult
                ));
    }

    private Flux<ServerSentEvent<String>> streamAnswer(Conversation conversation, String question,
                                                        List<ChatHistoryMessage> history,
                                                        List<RetrievedChunk> chunks,
                                                        QueryRewriteResult rewriteResult) {
        String prompt = rewriteResult.getNeedRetrieval()
                ? buildRagPrompt(question, history, chunks)
                : buildChatPrompt(question, history);
        String requestId = "chat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        StringBuilder answer = new StringBuilder();
        String[] usedProvider = {null};
        List<SourceReference> sources = chunks.stream()
                .map(chunk -> new SourceReference(
                        chunk.getDocId(),
                        chunk.getFileName(),
                        chunk.getSourceText()
                ))
                .toList();

        Flux<ServerSentEvent<String>> tokenEvents = llmGatewayService
                .streamChat(requestId, prompt, provider -> usedProvider[0] = provider)
                .doOnNext(token -> {
                    answer.append(token);
                })
                .map(token -> sse(new TokenSseEvent(token)));
        Flux<ServerSentEvent<String>> terminalEvents = Flux.defer(() -> {
            // token_count: OpenAI 兼容流式接口不提供 usage，保存 null 而非估算值
            submitPersistence(
                    conversation.getId(),
                    question,
                    answer.toString(),
                    rewriteResult,
                    sources,
                    usedProvider[0],
                    null
            );
            return Flux.just(
                    sse(new SourcesSseEvent(sources)),
                    sse(new DoneSseEvent())
            );
        });
        return tokenEvents.concatWith(terminalEvents);
    }

    private void submitPersistence(Long conversationId, String question, String answer,
                                   QueryRewriteResult rewriteResult,
                                   List<SourceReference> sources,
                                   String llmProvider, Integer estimatedTokens) {
        try {
            chatPersistenceService.persistExchange(
                    conversationId,
                    question,
                    answer,
                    rewriteResult.getRewrittenQuery(),
                    rewriteResult.getNeedRetrieval(),
                    sources,
                    llmProvider,
                    estimatedTokens
            );
        } catch (RuntimeException exception) {
            log.error("Failed to submit chat persistence task: conversationId={}",
                    conversationId, exception);
        }
    }

    private String buildChatPrompt(String question, List<ChatHistoryMessage> history) {
        StringBuilder historyText = new StringBuilder();
        for (ChatHistoryMessage message : history) {
            historyText.append(message.role())
                    .append("：")
                    .append(message.content())
                    .append('\n');
        }
        return """
                你是一个友好的智能助手。
                当前问题不需要查询知识库，请直接自然地回答用户。

                历史对话：
                %s
                用户问题：
                %s
                """.formatted(historyText, question);
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

    private String resolveErrorMessage(Throwable exception) {
        Throwable current = reactor.core.Exceptions.unwrap(exception);
        while (current != null) {
            if (current instanceof AllLlmProviderFailedException) {
                return "AI 服务暂时不可用，请稍后再试";
            }
            if (current instanceof com.example.smartkb.common.BusinessException) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "服务处理异常，请稍后重试";
    }
}
