package com.example.smartkb.chat.model;

public record TokenSseEvent(String type, String content) {

    public TokenSseEvent(String content) {
        this("token", content);
    }
}

