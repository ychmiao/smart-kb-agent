package com.example.smartkb.chat.model;

import java.util.List;

/**
 * SSE sources 事件 —— 回答引用的文档来源列表。
 * <p>
 * type: "sources"<br>
 * sources: 引用来源数组，每个包含 docId、fileName 和 excerpt（前 50 字摘要）
 */
public record SourcesSseEvent(String type, List<SourceReference> sources) {

    public SourcesSseEvent(List<SourceReference> sources) {
        this("sources", sources);
    }
}

