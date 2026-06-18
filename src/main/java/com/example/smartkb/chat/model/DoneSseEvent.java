package com.example.smartkb.chat.model;

public record DoneSseEvent(String type) {

    public DoneSseEvent() {
        this("done");
    }
}

