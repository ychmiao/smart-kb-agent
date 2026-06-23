package com.example.smartkb.document.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档分块 —— SemanticChunker 的输出，将被向量化后存入 Milvus。
 * <p>
 * sourceText 为 content 的前 50 个字，用于前端展示引用摘要。
 */
@Getter
@AllArgsConstructor
public class DocumentChunk {

    /** 所属文档 ID */
    private final Long documentId;
    /** 所属知识库 ID */
    private final Long kbId;
    /** 块序号（从 0 开始） */
    private final Integer chunkIndex;
    /** 分块文本内容 */
    private final String content;
    /** 引用摘要（前 50 字） */
    private final String sourceText;
}

