package com.example.smartkb.document.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.service.DocumentProcessingService;
import com.example.smartkb.document.service.DocumentService;
import com.example.smartkb.document.storage.MinioFileStorage;
import com.example.smartkb.document.vo.DocumentResponse;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.search.store.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 文档服务实现 —— 上传校验、MinIO 存储、异步处理触发和幂等删除。
 * <p>
 * 上传流程：校验（类型/MIME/大小）→ MinIO 上传 → MySQL 处理中记录 → 异步提交处理任务
 * 删除流程（幂等）：校验归属/状态 → Milvus 删除向量 → MinIO 删除文件 → MySQL 逻辑删除
 * 补偿：上传后若数据库写入失败，调用 {@code removeQuietly} 清理已上传的 MinIO 文件
 */
@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    /** 文件大小上限：50MB */
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    /** 文档初始 chunk 数 */
    private static final int INITIAL_CHUNK_COUNT = 0;
    /** 允许上传的文件扩展名 */
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "docx", "md", "txt");
    /** 各文件类型允许的 Content-Type（含 application/octet-stream 兜底） */
    private static final Map<String, Set<String>> ALLOWED_CONTENT_TYPES = Map.of(
            "pdf", Set.of("application/pdf", "application/octet-stream"),
            "docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/octet-stream"
            ),
            "md", Set.of("text/markdown", "text/plain", "application/octet-stream"),
            "txt", Set.of("text/plain", "application/octet-stream")
    );

    private final KnowledgeBaseService knowledgeBaseService;
    private final MinioFileStorage minioFileStorage;
    private final DocumentProcessingService documentProcessingService;
    private final MilvusVectorStore milvusVectorStore;

    public DocumentServiceImpl(KnowledgeBaseService knowledgeBaseService, MinioFileStorage minioFileStorage,
                               DocumentProcessingService documentProcessingService,
                               MilvusVectorStore milvusVectorStore) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.minioFileStorage = minioFileStorage;
        this.documentProcessingService = documentProcessingService;
        this.milvusVectorStore = milvusVectorStore;
    }

    @Override
    public Long upload(MultipartFile file, Long knowledgeBaseId) {
        knowledgeBaseService.requireCurrentUserKnowledgeBase(knowledgeBaseId);
        ValidatedFile validatedFile = validateFile(file);
        Long userId = UserContext.requireUserId();
        String objectName = buildObjectName(userId, knowledgeBaseId, validatedFile.fileType());

        minioFileStorage.upload(file, objectName);
        try {
            Document document = new Document();
            document.setKbId(knowledgeBaseId);
            document.setFileName(validatedFile.fileName());
            document.setFileType(validatedFile.fileType());
            document.setFileSize(file.getSize());
            document.setMinioPath(objectName);
            document.setChunkCount(INITIAL_CHUNK_COUNT);
            document.setStatus(DocumentStatus.PROCESSING.getCode());
            if (!save(document)) {
                throw new BusinessException(50011, "文档记录创建失败");
            }
            log.info("Document uploaded: documentId={}, kbId={}, userId={}, objectName={}",
                    document.getId(), knowledgeBaseId, userId, objectName);
            submitProcessingTask(document.getId());
            return document.getId();
        } catch (RuntimeException exception) {
            minioFileStorage.removeQuietly(objectName);
            throw exception;
        }
    }

    private void submitProcessingTask(Long documentId) {
        try {
            documentProcessingService.process(documentId);
        } catch (RuntimeException exception) {
            documentProcessingService.markSubmissionFailed(documentId, exception);
        }
    }

    @Override
    public List<DocumentResponse> listCurrentUserDocuments(Long knowledgeBaseId) {
        knowledgeBaseService.requireCurrentUserKnowledgeBase(knowledgeBaseId);
        return lambdaQuery()
                .eq(Document::getKbId, knowledgeBaseId)
                .orderByDesc(Document::getCreateTime)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public void deleteCurrentUserDocument(Long documentId) {
        Document document = getById(documentId);
        if (document == null) {
            throw new BusinessException(40410, "文档不存在");
        }
        knowledgeBaseService.requireCurrentUserKnowledgeBase(document.getKbId());
        if (DocumentStatus.PROCESSING.getCode() == document.getStatus()) {
            throw new BusinessException(40910, "文档正在处理中，请稍后再删除");
        }

        // 删除顺序：Milvus → MinIO → MySQL
        // 每一步都幂等，确保失败后可重试

        // Step 1: 删除 Milvus 向量（幂等；失败则抛出，不继续后续操作）
        milvusVectorStore.deleteDocument(document.getKbId(), document.getId());

        // Step 2: 删除 MinIO 文件（幂等；失败则抛出，不继续后续操作）
        if (document.getMinioPath() != null) {
            minioFileStorage.remove(document.getMinioPath());
        }

        // Step 3: MySQL 逻辑删除（@TableLogic 转为 UPDATE is_deleted = 1）
        if (!removeById(document.getId())) {
            // 未命中记录：可能是并发删除或 MySQL 短暂失败
            // 检查记录实际状态，确认是否已删除
            if (isDocumentAlreadyDeleted(documentId)) {
                log.warn("Document already deleted by concurrent request: documentId={}", documentId);
                return;
            }
            throw new BusinessException(50013, "文档记录删除失败，请重试");
        }

        log.info("Document deleted: documentId={}, kbId={}", document.getId(), document.getKbId());
    }

    private boolean isDocumentAlreadyDeleted(Long documentId) {
        try {
            Document currentDoc = getBaseMapper().selectById(documentId);
            return currentDoc == null || currentDoc.getIsDeleted() == 1;
        } catch (Exception e) {
            log.warn("Failed to check document deletion status: documentId={}", documentId, e);
            return false;
        }
    }

    @Override
    public Map<Long, String> getFileNames(Long knowledgeBaseId, Set<Long> documentIds) {
        knowledgeBaseService.requireCurrentUserKnowledgeBase(knowledgeBaseId);
        if (documentIds == null || documentIds.isEmpty()) {
            return Map.of();
        }
        return lambdaQuery()
                .eq(Document::getKbId, knowledgeBaseId)
                .in(Document::getId, documentIds)
                .list()
                .stream()
                .collect(java.util.stream.Collectors.toMap(Document::getId, Document::getFileName));
    }

    private ValidatedFile validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(40010, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(40011, "文件大小不能超过 50MB");
        }

        String fileName = normalizeFileName(file.getOriginalFilename());
        String fileType = extractFileType(fileName);
        if (!ALLOWED_FILE_TYPES.contains(fileType)) {
            throw new BusinessException(40012, "仅支持 pdf、docx、md、txt 文件");
        }

        String contentType = file.getContentType();
        String normalizedContentType = contentType == null
                ? ""
                : contentType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.get(fileType).contains(normalizedContentType)) {
            throw new BusinessException(40013, "文件内容类型与扩展名不匹配");
        }
        return new ValidatedFile(fileName, fileType);
    }

    private String normalizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new BusinessException(40012, "文件名不能为空");
        }
        String normalized = originalFileName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (fileName.isBlank() || fileName.contains("..") || fileName.length() > 255) {
            throw new BusinessException(40012, "文件名不合法");
        }
        return fileName;
    }

    private String extractFileType(String fileName) {
        int separatorIndex = fileName.lastIndexOf('.');
        if (separatorIndex < 1 || separatorIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String buildObjectName(Long userId, Long knowledgeBaseId, String fileType) {
        String objectId = UUID.randomUUID().toString().replace("-", "");
        return userId + "/" + knowledgeBaseId + "/" + objectId + "." + fileType;
    }

    private DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getKbId(),
                document.getFileName(),
                document.getFileType(),
                document.getFileSize(),
                document.getChunkCount(),
                document.getStatus(),
                document.getErrorMsg(),
                document.getCreateTime(),
                document.getUpdateTime()
        );
    }

    private record ValidatedFile(String fileName, String fileType) {
    }
}
