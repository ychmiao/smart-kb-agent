package com.example.smartkb.llm.service;

import reactor.core.publisher.Flux;

import java.util.List;

public interface LlmGatewayService {

    String chat(String requestId, String prompt);

    Flux<String> streamChat(String requestId, String prompt);

    List<Double> embedding(String requestId, String text);
}

