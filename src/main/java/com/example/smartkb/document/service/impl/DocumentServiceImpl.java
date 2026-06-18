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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final int INITIAL_CHUNK_COUNT = 0;
    private static final Set<String> ALLOWED_FILE_TYPES = Set.of("pdf", "docx", "md", "txt");
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

    public DocumentServiceImpl(KnowledgeBaseService knowledgeBaseService, MinioFileStorage minioFileStorage,
                               DocumentProcessingService documentProcessingService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.minioFileStorage = minioFileStorage;
        this.documentProcessingService = documentProcessingService;
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
