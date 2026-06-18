package com.example.smartkb.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class LlmHttpClientConfig {

    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    @Bean("llmWebClient")
    public WebClient llmWebClient(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .responseTimeout(RESPONSE_TIMEOUT);
        return builder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}

