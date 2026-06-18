package com.example.smartkb.document.service;

import com.example.smartkb.document.chunk.SemanticChunker;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentChunk;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.storage.MinioFileStorage;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
public class DocumentProcessingService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final DocumentMapper documentMapper;
    private final MinioFileStorage minioFileStorage;
    private final Tika tika;
    private final SemanticChunker semanticChunker;

    public DocumentProcessingService(DocumentMapper documentMapper, MinioFileStorage minioFileStorage,
                                     Tika tika, SemanticChunker semanticChunker) {
        this.documentMapper = documentMapper;
        this.minioFileStorage = minioFileStorage;
        this.tika = tika;
        this.semanticChunker = semanticChunker;
    }

    @Async("documentTaskExecutor")
    public void process(Long documentId) {
        Document document = documentMapper.selectById(documentId);
        if (document == null) {
            log.warn("Document processing skipped because record does not exist: documentId={}", documentId);
            return;
        }

        try (InputStream inputStream = minioFileStorage.download(document.getMinioPath())) {
            String text = tika.parseToString(inputStream);
            List<DocumentChunk> chunks = semanticChunker.chunk(document.getId(), document.getKbId(), text);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("文档未解析出有效文本");
            }

            updateCompleted(document, chunks.size());
            log.info("Document parsed successfully: documentId={}, chunkCount={}", documentId, chunks.size());
        } catch (Exception exception) {
            updateFailed(document, exception);
            log.error("Document parsing failed: documentId={}", documentId, exception);
        }
    }

    public void markSubmissionFailed(Long documentId, RuntimeException exception) {
        Document document = documentMapper.selectById(documentId);
        if (document != null) {
            updateFailed(document, exception);
        }
        log.error("Document task submission failed: documentId={}", documentId, exception);
    }

    private void updateCompleted(Document document, int chunkCount) {
        document.setStatus(DocumentStatus.COMPLETED.getCode());
        document.setChunkCount(chunkCount);
        document.setErrorMsg(null);
        ensureUpdated(document);
    }

    private void updateFailed(Document document, Exception exception) {
        document.setStatus(DocumentStatus.FAILED.getCode());
        document.setErrorMsg(buildErrorMessage(exception));
        ensureUpdated(document);
    }

    private void ensureUpdated(Document document) {
        if (documentMapper.updateById(document) != 1) {
            throw new IllegalStateException("文档状态更新失败: documentId=" + document.getId());
        }
    }

    private String buildErrorMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        String sanitized = message.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.substring(0, Math.min(MAX_ERROR_MESSAGE_LENGTH, sanitized.length()));
    }
}

