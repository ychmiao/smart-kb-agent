package com.example.smartkb.document.service;

import com.example.smartkb.document.entity.Document;

import java.util.List;

/**
 * 文档清理服务 — 仅依赖 DocumentMapper + MinioFileStorage，不依赖 KnowledgeBaseService。
 * 用于 KnowledgeBaseServiceImpl 在删除知识库时安全地清理关联文档，不产生循环依赖。
 */
public interface DocumentCleanupService {

    /**
     * 查询指定知识库下所有未逻辑删除的文档（@TableLogic 自动过滤 is_deleted=1）。
     */
    List<Document> listByKbId(Long kbId);

    /**
     * 检查指定知识库是否存在处理中的文档。
     */
    boolean hasProcessingDocuments(Long kbId);

    /**
     * 删除指定知识库下所有文档的 MinIO 文件（严格模式，失败即抛异常）。
     * 操作幂等：对已删除或空知识库执行无副作用。
     *
     * @param kbId 知识库 ID
     * @throws com.example.smartkb.common.BusinessException MinIO 删除失败时抛出
     */
    void deleteMinioFilesByKbId(Long kbId);

    /**
     * 删除指定知识库下所有文档的 MySQL 记录（@TableLogic 逻辑删除）。
     * 操作幂等：重复执行返回 0 无影响。
     */
    int deleteDocumentRecordsByKbId(Long kbId);
}
