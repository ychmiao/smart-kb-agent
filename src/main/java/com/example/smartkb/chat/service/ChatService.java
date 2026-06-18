package com.example.smartkb.chat.service;

import com.example.smartkb.chat.dto.ChatStreamRequest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface ChatService {

    Flux<ServerSentEvent<String>> stream(ChatStreamRequest request);
}

