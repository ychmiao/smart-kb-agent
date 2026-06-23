package com.example.smartkb.llm.client;

import com.example.smartkb.llm.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiCompatibleClientTest {

    private MockWebServer mockWebServer;
    private OpenAiCompatibleClient client;
    private LlmProperties.Provider provider;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        client = new OpenAiCompatibleClient(webClient, new ObjectMapper());
        provider = new LlmProperties.Provider();
        provider.setBaseUrl(mockWebServer.url("/").toString());
        provider.setApiKey("test-key");
        provider.setChatModel("test-model");
        provider.setEmbeddingModel("test-embedding-model");
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void shouldSendChatRequestAndReceiveResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"content\":\"Hello!\"}}]}")
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
        );

        String answer = client.chat("test", provider, "Hi");

        assertThat(answer).isEqualTo("Hello!");

        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-key");
        assertThat(request.getBody().readUtf8()).contains("test-model")
                .contains("\"stream\":false")
                .contains("\"role\":\"user\"")
                .contains("\"content\":\"Hi\"");
    }

    @Test
    void shouldThrowOnHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":\"unauthorized\"}")
                .setHeader("Content-Type", "application/json")
        );

        assertThatThrownBy(() -> client.chat("test", provider, "Hi"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("HTTP 401");
    }

    @Test
    void shouldThrowOnEmptyResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"choices\":[{\"message\":{\"content\":\"\"}}]}")
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
        );

        assertThatThrownBy(() -> client.chat("test", provider, "Hi"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("返回内容为空");
    }

    @Test
    void shouldStreamTokensFromSseEvents() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
                .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n"
                        + "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n\n"
                        + "data: [DONE]\n\n")
        );

        List<String> tokens = client.streamChat("test", provider, "Hi").collectList().block();

        assertThat(tokens).containsExactly("Hello", " world");
    }

    @Test
    void shouldSendEmbeddingRequestAndReceiveVector() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}")
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
        );

        List<Double> vector = client.embedding("test", provider, "some text");

        assertThat(vector).containsExactly(0.1, 0.2, 0.3);

        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getBody().readUtf8()).contains("test-embedding-model")
                .contains("\"input\":\"some text\"");
    }

    @Test
    void shouldThrowOnEmbeddingHttpError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("{\"error\":\"server error\"}")
        );

        assertThatThrownBy(() -> client.embedding("test", provider, "text"))
                .isInstanceOf(LlmProviderException.class)
                .hasMessageContaining("HTTP 500");
    }
}
