package com.example.smartkb.chat.model;

public record RewriteSseEvent(String type, boolean needRetrieval, String rewrittenQuery) {

    public RewriteSseEvent(boolean needRetrieval, String rewrittenQuery) {
        this("rewrite", needRetrieval, rewrittenQuery);
    }
}
