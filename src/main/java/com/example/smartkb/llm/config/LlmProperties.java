package com.example.smartkb.llm.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "kb.llm")
public class LlmProperties {

    @Valid
    @NotNull
    private Providers providers;

    @Getter
    @Setter
    public static class Providers {

        @Valid
        @NotNull
        private Provider deepseek;

        @Valid
        @NotNull
        private Provider qwen;
    }

    @Getter
    @Setter
    public static class Provider {

        @NotBlank
        private String baseUrl;

        @NotBlank
        private String apiKey;

        @NotBlank
        private String chatModel;

        private String embeddingModel;

        private int priority;
    }
}

