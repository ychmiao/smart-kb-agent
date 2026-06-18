package com.example.smartkb.chat.model;

import java.util.List;

public record SourcesSseEvent(String type, List<SourceReference> sources) {

    public SourcesSseEvent(List<SourceReference> sources) {
        this("sources", sources);
    }
}

