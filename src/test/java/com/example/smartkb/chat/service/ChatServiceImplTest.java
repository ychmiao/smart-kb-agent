package com.example.smartkb.chat.service;

import com.example.smartkb.chat.dto.ChatStreamRequest;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.service.impl.ChatServiceImpl;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.model.QueryRewriteResult;
import com.example.smartkb.search.service.QueryRewriteService;
import com.example.smartkb.search.service.RetrievalService;
import com.example.smartkb.search.vo.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ConversationService conversationService;

    @Mock
    private ChatHistoryService chatHistoryService;

    @Mock
    private QueryRewriteService queryRewriteService;

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private LlmGatewayService llmGatewayService;

    @Mock
    private ChatPersistenceService chatPersistenceService;

    private ObjectMapper objectMapper;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        chatService = new ChatServiceImpl(
                conversationService,
                chatHistoryService,
                queryRewriteService,
                retrievalService,
                llmGatewayService,
                chatPersistenceService,
                objectMapper
        );
        UserContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldStreamEventsInRequiredOrderAndPersistAnswer() throws Exception {
        ChatStreamRequest request = new ChatStreamRequest();
        request.setKbId(1L);
        request.setQuestion("如何使用？");
        Conversation conversation = new Conversation();
        conversation.setId(20L);
        conversation.setKbId(1L);
        conversation.setUserId(7L);
        RetrievedChunk chunk = new RetrievedChunk(
                30L,
                0,
                "使用说明",
                "使用说明摘要",
                0.9,
                "manual.pdf"
        );
        when(conversationService.getOrCreate(null, 1L, 7L, "如何使用？"))
                .thenReturn(conversation);
        when(chatHistoryService.getRecentHistory(20L)).thenReturn(List.of());
        when(queryRewriteService.rewrite("如何使用？", List.of()))
                .thenReturn(new QueryRewriteResult(true, "如何使用？"));
        when(retrievalService.retrieve(1L, "如何使用？", 5)).thenReturn(List.of(chunk));
        when(llmGatewayService.streamChat(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("使用说明")))
                .thenReturn(Flux.just("请先", "阅读手册"));

        List<ServerSentEvent<String>> events = chatService.stream(request).collectList().block();

        assertThat(events).isNotNull().hasSize(5);
        assertThat(eventType(events.get(0))).isEqualTo("rewrite");
        assertThat(eventType(events.get(1))).isEqualTo("token");
        assertThat(eventType(events.get(2))).isEqualTo("token");
        assertThat(eventType(events.get(3))).isEqualTo("sources");
        assertThat(eventType(events.get(4))).isEqualTo("done");
        assertThat(objectMapper.readTree(events.get(1).data()).path("content").asText()).isEqualTo("请先");
        assertThat(objectMapper.readTree(events.get(3).data()).path("sources").path(0)
                .path("fileName").asText()).isEqualTo("manual.pdf");

        ArgumentCaptor<List> sourcesCaptor = ArgumentCaptor.forClass(List.class);
        verify(chatPersistenceService).persistExchange(
                eq(20L),
                eq("如何使用？"),
                eq("请先阅读手册"),
                eq("如何使用？"),
                eq(true),
                sourcesCaptor.capture()
        );
        assertThat(sourcesCaptor.getValue()).hasSize(1);
        verify(retrievalService).retrieve(1L, "如何使用？", 5);
    }

    @Test
    void shouldSkipRetrievalForSmallTalk() throws Exception {
        ChatStreamRequest request = new ChatStreamRequest();
        request.setKbId(1L);
        request.setQuestion("你好");
        Conversation conversation = new Conversation();
        conversation.setId(21L);
        conversation.setKbId(1L);
        conversation.setUserId(7L);
        when(conversationService.getOrCreate(null, 1L, 7L, "你好")).thenReturn(conversation);
        when(chatHistoryService.getRecentHistory(21L)).thenReturn(List.of());
        when(queryRewriteService.rewrite("你好", List.of()))
                .thenReturn(new QueryRewriteResult(false, "你好"));
        when(llmGatewayService.streamChat(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.contains("不需要查询知识库")))
                .thenReturn(Flux.just("你好！"));

        List<ServerSentEvent<String>> events = chatService.stream(request).collectList().block();

        assertThat(events).isNotNull().hasSize(4);
        assertThat(eventType(events.get(0))).isEqualTo("rewrite");
        assertThat(objectMapper.readTree(events.get(0).data()).path("needRetrieval").asBoolean()).isFalse();
        assertThat(eventType(events.get(1))).isEqualTo("token");
        assertThat(eventType(events.get(2))).isEqualTo("sources");
        assertThat(objectMapper.readTree(events.get(2).data()).path("sources")).isEmpty();
        assertThat(eventType(events.get(3))).isEqualTo("done");
        verify(retrievalService, never()).retrieve(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt()
        );
        verify(chatPersistenceService).persistExchange(
                eq(21L), eq("你好"), eq("你好！"), eq("你好"), eq(false), eq(List.of())
        );
    }

    private String eventType(ServerSentEvent<String> event) throws Exception {
        JsonNode root = objectMapper.readTree(event.data());
        return root.path("type").asText();
    }
}
