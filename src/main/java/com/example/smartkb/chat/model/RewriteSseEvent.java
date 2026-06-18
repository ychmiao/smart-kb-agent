package com.example.smartkb.chat.model;

public record RewriteSseEvent(String type, boolean needRetrieval, String rewrittenQuery) {

    public RewriteSseEvent(String rewrittenQuery) {
        this("rewrite", true, rewrittenQuery);
    }
}

