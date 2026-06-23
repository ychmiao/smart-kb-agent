package com.example.smartkb.chat.model;

/**
 * SSE sources 事件中的单个引用来源。
 *
 * @param docId    文档 ID
 * @param fileName 文档名称（前端展示用）
 * @param excerpt  引用摘要（chunk 前 50 字）
 */
public record SourceReference(Long docId, String fileName, String excerpt) {
}

