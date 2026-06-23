package com.example.smartkb.chat.model;

/**
 * SSE rewrite 事件 —— 查询重写结果。
 * <p>
 * type: "rewrite"<br>
 * needRetrieval: 是否需要检索知识库<br>
 * rewrittenQuery: 改写后的查询（若 needRetrieval=false 则为原始问题）
 */
public record RewriteSseEvent(String type, boolean needRetrieval, String rewrittenQuery) {

    public RewriteSseEvent(boolean needRetrieval, String rewrittenQuery) {
        this("rewrite", needRetrieval, rewrittenQuery);
    }
}
