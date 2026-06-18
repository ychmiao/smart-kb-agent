package com.example.smartkb.chat.model;

public record ErrorSseEvent(String type, String message) {

    public ErrorSseEvent(String message) {
        this("error", message);
    }
}

