package com.example.smartkb.llm.client;

import lombok.Getter;

@Getter
public class LlmProviderException extends RuntimeException {

    private final String providerName;

    public LlmProviderException(String providerName, String message) {
        super(message);
        this.providerName = providerName;
    }

    public LlmProviderException(String providerName, String message, Throwable cause) {
        super(message, cause);
        this.providerName = providerName;
    }
}

