package com.example.smartkb.llm.service;

import com.example.smartkb.llm.client.LlmProviderException;
import com.example.smartkb.llm.client.OpenAiCompatibleClient;
import com.example.smartkb.llm.config.LlmProperties;
import com.example.smartkb.llm.exception.AllLlmProviderFailedException;
import com.example.smartkb.llm.service.impl.LlmGatewayServiceImpl;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmGatewayServiceImplTest {

    @Mock
    private OpenAiCompatibleClient client;

    @Mock
    private LlmCallLogService callLogService;

    private LlmProperties.Provider deepSeek;
    private LlmProperties.Provider qwen;
    private LlmGatewayService gatewayService;
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void setUp() {
        deepSeek = provider("deepseek-chat", null);
        qwen = provider("qwen-plus", "text-embedding-v3");

        LlmProperties.Providers providers = new LlmProperties.Providers();
        providers.setDeepseek(deepSeek);
        providers.setQwen(qwen);
        LlmProperties properties = new LlmProperties();
        properties.setProviders(providers);

        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        gatewayService = new LlmGatewayServiceImpl(
                client,
                properties,
                callLogService,
                circuitBreakerRegistry
        );
    }

    @Test
    void shouldFallbackToQwenWhenDeepSeekFails() {
        when(client.chat("deepseek", deepSeek, "question"))
                .thenThrow(new LlmProviderException("deepseek", "failed"));
        when(client.chat("qwen", qwen, "question")).thenReturn("answer");

        String answer = gatewayService.chat("request-1", "question");

        assertThat(answer).isEqualTo("answer");
        verify(client).chat("deepseek", deepSeek, "question");
        verify(client).chat("qwen", qwen, "question");
        verify(callLogService).recordAsync(
                eq("request-1"), eq("chat"), eq("deepseek"), eq(false), anyLong(), eq("failed")
        );
        verify(callLogService).recordAsync(
                eq("request-1"), eq("chat"), eq("qwen"), eq(true), anyLong(), eq(null)
        );
    }

    @Test
    void shouldUseOnlyQwenForEmbedding() {
        List<Double> expected = List.of(0.1, 0.2, 0.3);
        when(client.embedding("qwen", qwen, "text")).thenReturn(expected);

        List<Double> result = gatewayService.embedding("request-2", "text");

        assertThat(result).isEqualTo(expected);
        verify(client).embedding("qwen", qwen, "text");
        verify(client, never()).embedding("deepseek", deepSeek, "text");
    }

    @Test
    void shouldThrowUnifiedExceptionWhenBothChatProvidersFail() {
        when(client.chat("deepseek", deepSeek, "question"))
                .thenThrow(new LlmProviderException("deepseek", "deepseek failed"));
        when(client.chat("qwen", qwen, "question"))
                .thenThrow(new LlmProviderException("qwen", "qwen failed"));

        assertThatThrownBy(() -> gatewayService.chat("request-3", "question"))
                .isInstanceOf(AllLlmProviderFailedException.class)
                .hasMessage("AI 服务暂时不可用，请稍后再试");
    }

    @Test
    void shouldSkipDeepSeekWhenCircuitBreakerIsOpen() {
        circuitBreakerRegistry.circuitBreaker("deepseek").transitionToOpenState();
        when(client.chat("qwen", qwen, "question")).thenReturn("qwen answer");

        String answer = gatewayService.chat("request-4", "question");

        assertThat(answer).isEqualTo("qwen answer");
        verify(client, never()).chat("deepseek", deepSeek, "question");
        verify(client).chat("qwen", qwen, "question");
        verify(callLogService).recordAsync(
                eq("request-4"),
                eq("chat"),
                eq("deepseek"),
                eq(false),
                anyLong(),
                anyString()
        );
    }

    private LlmProperties.Provider provider(String chatModel, String embeddingModel) {
        LlmProperties.Provider provider = new LlmProperties.Provider();
        provider.setBaseUrl("https://example.com");
        provider.setApiKey("key");
        provider.setChatModel(chatModel);
        provider.setEmbeddingModel(embeddingModel);
        return provider;
    }
}
