package com.example.smartkb.llm.service.impl;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.llm.client.LlmProviderException;
import com.example.smartkb.llm.client.OpenAiCompatibleClient;
import com.example.smartkb.llm.config.LlmProperties;
import com.example.smartkb.llm.exception.AllLlmProviderFailedException;
import com.example.smartkb.llm.service.LlmCallLogService;
import com.example.smartkb.llm.service.LlmGatewayService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class LlmGatewayServiceImpl implements LlmGatewayService {

    private static final String DEEPSEEK = "deepseek";
    private static final String QWEN = "qwen";
    private static final String CHAT = "chat";
    private static final String STREAM_CHAT = "stream_chat";
    private static final String EMBEDDING = "embedding";

    private final OpenAiCompatibleClient client;
    private final LlmProperties properties;
    private final LlmCallLogService callLogService;
    private final CircuitBreaker deepSeekCircuitBreaker;
    private final CircuitBreaker qwenCircuitBreaker;

    public LlmGatewayServiceImpl(OpenAiCompatibleClient client, LlmProperties properties,
                                 LlmCallLogService callLogService,
                                 CircuitBreakerRegistry circuitBreakerRegistry) {
        this.client = client;
        this.properties = properties;
        this.callLogService = callLogService;
        this.deepSeekCircuitBreaker = circuitBreakerRegistry.circuitBreaker(DEEPSEEK);
        this.qwenCircuitBreaker = circuitBreakerRegistry.circuitBreaker(QWEN);
    }

    @Override
    public String chat(String requestId, String prompt) {
        String normalizedRequestId = normalizeRequestId(requestId);
        validateText(prompt, "prompt");
        try {
            return callChatProvider(
                    normalizedRequestId,
                    DEEPSEEK,
                    properties.getProviders().getDeepseek(),
                    deepSeekCircuitBreaker,
                    prompt
            );
        } catch (LlmProviderException deepSeekException) {
            log.warn("DeepSeek chat failed, falling back to Qwen: requestId={}", normalizedRequestId);
            try {
                return callChatProvider(
                        normalizedRequestId,
                        QWEN,
                        properties.getProviders().getQwen(),
                        qwenCircuitBreaker,
                        prompt
                );
            } catch (LlmProviderException qwenException) {
                qwenException.addSuppressed(deepSeekException);
                throw new AllLlmProviderFailedException(qwenException);
            }
        }
    }

    @Override
    public Flux<String> streamChat(String requestId, String prompt) {
        String normalizedRequestId = normalizeRequestId(requestId);
        validateText(prompt, "prompt");
        AtomicBoolean deepSeekEmitted = new AtomicBoolean(false);
        return streamChatProvider(
                normalizedRequestId,
                DEEPSEEK,
                properties.getProviders().getDeepseek(),
                deepSeekCircuitBreaker,
                prompt
        )
                .doOnNext(token -> deepSeekEmitted.set(true))
                .onErrorResume(exception -> {
                    if (deepSeekEmitted.get()) {
                        return Flux.error(new AllLlmProviderFailedException(exception));
                    }
                    log.warn("DeepSeek stream chat failed, falling back to Qwen: requestId={}",
                            normalizedRequestId);
                    return streamChatProvider(
                            normalizedRequestId,
                            QWEN,
                            properties.getProviders().getQwen(),
                            qwenCircuitBreaker,
                            prompt
                    ).onErrorMap(AllLlmProviderFailedException::new);
                });
    }

    @Override
    public List<Double> embedding(String requestId, String text) {
        String normalizedRequestId = normalizeRequestId(requestId);
        validateText(text, "text");
        long startNanos = System.nanoTime();
        try {
            List<Double> vector = qwenCircuitBreaker.executeSupplier(() -> client.embedding(
                    QWEN, properties.getProviders().getQwen(), text
            ));
            recordSafely(normalizedRequestId, EMBEDDING, QWEN, true, elapsedMillis(startNanos), null);
            return vector;
        } catch (RuntimeException exception) {
            recordSafely(normalizedRequestId, EMBEDDING, QWEN, false,
                    elapsedMillis(startNanos), exception.getMessage());
            throw new AllLlmProviderFailedException(toProviderException(QWEN, exception));
        }
    }

    private String callChatProvider(String requestId, String providerName,
                                    LlmProperties.Provider provider,
                                    CircuitBreaker circuitBreaker,
                                    String prompt) {
        long startNanos = System.nanoTime();
        try {
            String response = circuitBreaker.executeSupplier(
                    () -> client.chat(providerName, provider, prompt)
            );
            recordSafely(requestId, CHAT, providerName, true, elapsedMillis(startNanos), null);
            return response;
        } catch (RuntimeException exception) {
            recordSafely(requestId, CHAT, providerName, false,
                    elapsedMillis(startNanos), exception.getMessage());
            throw toProviderException(providerName, exception);
        }
    }

    private Flux<String> streamChatProvider(String requestId, String providerName,
                                            LlmProperties.Provider provider,
                                            CircuitBreaker circuitBreaker,
                                            String prompt) {
        return Flux.defer(() -> {
            long startNanos = System.nanoTime();
            return client.streamChat(providerName, provider, prompt)
                    .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                    .doOnComplete(() -> recordSafely(
                            requestId,
                            STREAM_CHAT,
                            providerName,
                            true,
                            elapsedMillis(startNanos),
                            null
                    ))
                    .doOnError(exception -> recordSafely(
                            requestId,
                            STREAM_CHAT,
                            providerName,
                            false,
                            elapsedMillis(startNanos),
                            exception.getMessage()
                    ))
                    .doOnCancel(() -> recordSafely(
                            requestId,
                            STREAM_CHAT,
                            providerName,
                            false,
                            elapsedMillis(startNanos),
                            "流式调用被客户端取消"
                    ));
        });
    }

    private LlmProviderException toProviderException(String providerName, RuntimeException exception) {
        if (exception instanceof LlmProviderException providerException) {
            return providerException;
        }
        if (exception instanceof CallNotPermittedException) {
            return new LlmProviderException(providerName, "模型服务熔断器已开启", exception);
        }
        return new LlmProviderException(providerName, "模型服务调用失败", exception);
    }

    private void recordSafely(String requestId, String callType, String providerName,
                              boolean success, long latencyMillis, String errorMessage) {
        try {
            callLogService.recordAsync(
                    requestId,
                    callType,
                    providerName,
                    success,
                    latencyMillis,
                    errorMessage
            );
        } catch (RuntimeException exception) {
            log.error("Failed to submit LLM call log: requestId={}, callType={}, provider={}",
                    requestId, callType, providerName, exception);
        }
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        if (requestId.length() > 64) {
            throw new BusinessException(40020, "requestId 长度不能超过 64 个字符");
        }
        return requestId;
    }

    private void validateText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(40020, fieldName + " 不能为空");
        }
    }
}
