package com.example.smartkb.document.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.service.DocumentCleanupService;
import com.example.smartkb.document.storage.MinioFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文档清理服务实现。
 * 只依赖 DocumentMapper（MyBatis-Plus 基础 Mapper）+ MinioFileStorage，
 * 不依赖任何 KnowledgeBaseService，从而避免循环依赖。
 * <p>
 * 所有 public 方法均幂等、可重试。
 */
@Slf4j
@Service
public class DocumentCleanupServiceImpl implements DocumentCleanupService {

    private final DocumentMapper documentMapper;
    private final MinioFileStorage minioFileStorage;

    public DocumentCleanupServiceImpl(DocumentMapper documentMapper,
                                      MinioFileStorage minioFileStorage) {
        this.documentMapper = documentMapper;
        this.minioFileStorage = minioFileStorage;
    }

    @Override
    public List<Document> listByKbId(Long kbId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getKbId, kbId);
        return documentMapper.selectList(queryWrapper);
    }

    @Override
    public boolean hasProcessingDocuments(Long kbId) {
        LambdaQueryWrapper<Document> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Document::getKbId, kbId)
                .eq(Document::getStatus, DocumentStatus.PROCESSING.getCode())
                .last("LIMIT 1");
        return documentMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public void deleteMinioFilesByKbId(Long kbId) {
        List<Document> documents = listByKbId(kbId);
        for (Document document : documents) {
            if (document.getMinioPath() != null) {
                // 严格模式：失败即抛异常，调用方获知后可重试
                minioFileStorage.remove(document.getMinioPath());
                log.debug("MinIO file deleted during KB cleanup: path={}, kbId={}",
                        document.getMinioPath(), kbId);
            }
        }
        log.info("MinIO files cleaned up for kbId={}, count={}", kbId, documents.size());
    }

    @Override
    public int deleteDocumentRecordsByKbId(Long kbId) {
        LambdaQueryWrapper<Document> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(Document::getKbId, kbId);
        int deleted = documentMapper.delete(deleteWrapper);
        log.info("Document records deleted for kbId={}, count={}", kbId, deleted);
        return deleted;
    }
}
