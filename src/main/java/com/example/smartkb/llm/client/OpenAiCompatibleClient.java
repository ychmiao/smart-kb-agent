package com.example.smartkb.llm.client;

import com.example.smartkb.llm.config.LlmProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.util.List;

@Component
public class OpenAiCompatibleClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String DONE_EVENT = "[DONE]";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleClient(@Qualifier("llmWebClient") WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    public String chat(String providerName, LlmProperties.Provider provider, String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                provider.getChatModel(),
                List.of(new ChatMessage("user", prompt)),
                false
        );
        try {
            JsonNode response = webClient.post()
                    .uri(buildUrl(provider.getBaseUrl(), CHAT_COMPLETIONS_PATH))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            String content = response == null
                    ? null
                    : response.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new LlmProviderException(providerName, "模型返回内容为空");
            }
            return content;
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw new LlmProviderException(
                    providerName,
                    "模型接口返回 HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new LlmProviderException(providerName, "模型接口调用失败", exception);
        }
    }

    public Flux<String> streamChat(String providerName, LlmProperties.Provider provider, String prompt) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                provider.getChatModel(),
                List.of(new ChatMessage("user", prompt)),
                true
        );
        return webClient.post()
                .uri(buildUrl(provider.getBaseUrl(), CHAT_COMPLETIONS_PATH))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(request)
                .retrieve()
                .bodyToFlux(String.class)
                .filter(event -> !event.isBlank() && !DONE_EVENT.equals(event))
                .map(event -> extractStreamContent(providerName, event))
                .filter(content -> !content.isEmpty())
                .onErrorMap(
                        exception -> !(exception instanceof LlmProviderException),
                        exception -> toProviderException(providerName, exception)
                );
    }

    public List<Double> embedding(String providerName, LlmProperties.Provider provider, String text) {
        if (provider.getEmbeddingModel() == null || provider.getEmbeddingModel().isBlank()) {
            throw new LlmProviderException(providerName, "未配置 Embedding 模型");
        }
        EmbeddingRequest request = new EmbeddingRequest(provider.getEmbeddingModel(), text);
        try {
            JsonNode response = webClient.post()
                    .uri(buildUrl(provider.getBaseUrl(), EMBEDDINGS_PATH))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            JsonNode embeddingNode = response == null
                    ? null
                    : response.path("data").path(0).path("embedding");
            if (embeddingNode == null || !embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new LlmProviderException(providerName, "Embedding 返回向量为空");
            }
            return objectMapper.convertValue(
                    embeddingNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)
            );
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            throw new LlmProviderException(
                    providerName,
                    "Embedding 接口返回 HTTP " + exception.getStatusCode().value(),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new LlmProviderException(providerName, "Embedding 接口调用失败", exception);
        }
    }

    private String extractStreamContent(String providerName, String event) {
        try {
            JsonNode root = objectMapper.readTree(event);
            return root.path("choices").path(0).path("delta").path("content").asText("");
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException(providerName, "流式响应格式无效", exception);
        }
    }

    private LlmProviderException toProviderException(String providerName, Throwable exception) {
        if (exception instanceof WebClientResponseException responseException) {
            return new LlmProviderException(
                    providerName,
                    "模型接口返回 HTTP " + responseException.getStatusCode().value(),
                    responseException
            );
        }
        return new LlmProviderException(providerName, "流式模型接口调用失败", exception);
    }

    private String buildUrl(String baseUrl, String path) {
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + path
                : baseUrl + path;
    }

    private record ChatCompletionRequest(String model, List<ChatMessage> messages, boolean stream) {
    }

    private record ChatMessage(String role, String content) {
    }

    private record EmbeddingRequest(String model, String input) {
    }
}
