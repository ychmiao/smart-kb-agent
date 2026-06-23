package com.example.smartkb.search.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 向量检索结果 —— Milvus 搜索返回的原始匹配项。
 * <p>
 * 后续由 RetrievalService 通过 DocumentService 补充 fileName 后转为 RetrievedChunk。
 */
@Getter
@AllArgsConstructor
public class VectorSearchResult {

    /** 文档 ID */
    private final Long docId;
    /** chunk 序号 */
    private final Integer chunkIndex;
    /** 文本内容 */
    private final String content;
    /** 引用摘要 */
    private final String sourceText;
    /** COSINE 相似度分数（0~1，越高越相似） */
    private final Double score;
}

