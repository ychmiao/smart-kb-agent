package com.example.smartkb.search.service;

import com.example.smartkb.chat.model.ChatHistoryMessage;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.model.QueryRewriteResult;
import com.example.smartkb.search.service.impl.QueryRewriteServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceImplTest {

    @Mock
    private LlmGatewayService llmGatewayService;

    private QueryRewriteService queryRewriteService;

    @BeforeEach
    void setUp() {
        queryRewriteService = new QueryRewriteServiceImpl(llmGatewayService, new ObjectMapper());
    }

    @Test
    void shouldRewriteReferentialQuestionWithRecentTwoRounds() {
        List<ChatHistoryMessage> history = List.of(
                new ChatHistoryMessage("user", "不应进入 Prompt"),
                new ChatHistoryMessage("assistant", "不应进入 Prompt"),
                new ChatHistoryMessage("user", "什么是 RAG？"),
                new ChatHistoryMessage("assistant", "RAG 是检索增强生成。"),
                new ChatHistoryMessage("user", "它有什么优点？"),
                new ChatHistoryMessage("assistant", "可以使用私有知识。")
        );
        when(llmGatewayService.chat(anyString(), anyString())).thenReturn(
                "{\"needRetrieval\":true,\"rewrittenQuery\":\"RAG 有什么优点？\"}"
        );

        QueryRewriteResult result = queryRewriteService.rewrite("它适合什么场景？", history);

        assertThat(result.getNeedRetrieval()).isTrue();
        assertThat(result.getRewrittenQuery()).isEqualTo("RAG 有什么优点？");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmGatewayService).chat(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains("什么是 RAG？", "它有什么优点？");
        assertThat(promptCaptor.getValue()).doesNotContain("不应进入 Prompt");
    }

    @Test
    void shouldIdentifySmallTalkWithoutRetrieval() {
        when(llmGatewayService.chat(anyString(), anyString())).thenReturn(
                "{\"needRetrieval\":false,\"rewrittenQuery\":\"你好\"}"
        );

        QueryRewriteResult result = queryRewriteService.rewrite("你好", List.of());

        assertThat(result.getNeedRetrieval()).isFalse();
        assertThat(result.getRewrittenQuery()).isEqualTo("你好");
    }

    @Test
    void shouldClassifyGeneralLifeQuestionWithoutRetrieval() {
        when(llmGatewayService.chat(anyString(), anyString())).thenReturn(
                "```json\n{\"needRetrieval\":false,\"rewrittenQuery\":\"一天吃几顿饭合适\"}\n```"
        );

        QueryRewriteResult result = queryRewriteService.rewrite("一天吃几顿饭合适", List.of());

        assertThat(result.getNeedRetrieval()).isFalse();
        assertThat(result.getRewrittenQuery()).isEqualTo("一天吃几顿饭合适");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmGatewayService).chat(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains(
                "闲聊、生活建议",
                "一天吃几顿饭合适",
                "needRetrieval=false"
        );
    }

    @Test
    void shouldPreferRetrievalForDomainQuestionWithoutExplicitDocumentWording() {
        when(llmGatewayService.chat(anyString(), anyString())).thenReturn(
                "{\"needRetrieval\":true,\"rewrittenQuery\":\"试用期多久\"}"
        );

        QueryRewriteResult result = queryRewriteService.rewrite("试用期多久", List.of());

        assertThat(result.getNeedRetrieval()).isTrue();
        assertThat(result.getRewrittenQuery()).isEqualTo("试用期多久");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmGatewayService).chat(anyString(), promptCaptor.capture());
        assertThat(promptCaptor.getValue()).contains(
                "不要求用户明确说“知识库”或“文档”",
                "无法确定是否与知识库相关时，优先 needRetrieval=true"
        );
    }

    @Test
    void shouldFallbackWhenJsonIsInvalid() {
        when(llmGatewayService.chat(anyString(), anyString())).thenReturn("```json\n{}\n```");

        QueryRewriteResult result = queryRewriteService.rewrite("原始问题", List.of());

        assertThat(result.getNeedRetrieval()).isTrue();
        assertThat(result.getRewrittenQuery()).isEqualTo("原始问题");
    }

    @Test
    void shouldFallbackWhenModelCallFails() {
        when(llmGatewayService.chat(anyString(), anyString()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        QueryRewriteResult result = queryRewriteService.rewrite("原始问题", List.of());

        assertThat(result.getNeedRetrieval()).isTrue();
        assertThat(result.getRewrittenQuery()).isEqualTo("原始问题");
    }
}

