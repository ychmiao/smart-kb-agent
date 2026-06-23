package com.example.smartkb.document.service;

import com.example.smartkb.document.chunk.SemanticChunker;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentChunk;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.storage.MinioFileStorage;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.store.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

/**
 * 文档异步处理服务 —— 执行 Tika 解析 → 语义分块 → Embedding → Milvus 入库。
 * <p>
 * 由 {@link DocumentServiceImpl#submitProcessingTask} 通过 {@code @Async("documentTaskExecutor")} 触发。
 * 处理成功更新文档状态为 COMPLETED，失败更新为 FAILED 并记录可诊断的错误信息。
 * 整个流程在独立线程池中执行，不阻塞上传接口返回。
 */
@Slf4j
@Service
public class DocumentProcessingService {

    /** 错误消息最大长度，避免存储过长的异常堆栈 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final DocumentMapper documentMapper;
    private final MinioFileStorage minioFileStorage;
    private final Tika tika;
    private final SemanticChunker semanticChunker;
    private final LlmGatewayService llmGatewayService;
    private final MilvusVectorStore milvusVectorStore;

    public DocumentProcessingService(DocumentMapper documentMapper, MinioFileStorage minioFileStorage,
                                     Tika tika, SemanticChunker semanticChunker,
                                     LlmGatewayService llmGatewayService, MilvusVectorStore milvusVectorStore) {
        this.documentMapper = documentMapper;
        this.minioFileStorage = minioFileStorage;
        this.tika = tika;
        this.semanticChunker = semanticChunker;
        this.llmGatewayService = llmGatewayService;
        this.milvusVectorStore = milvusVectorStore;
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

            List<List<Double>> embeddings = chunks.stream()
                    .map(chunk -> llmGatewayService.embedding(
                            embeddingRequestId(documentId, chunk.getChunkIndex()),
                            chunk.getContent()
                    ))
                    .toList();
            milvusVectorStore.insertChunks(document.getKbId(), chunks, embeddings);
            updateCompleted(document, chunks.size());
            log.info("Document parsed and vectorized successfully: documentId={}, chunkCount={}",
                    documentId, chunks.size());
        } catch (Exception exception) {
            updateFailed(document, exception);
            log.error("Document parsing failed: documentId={}", documentId, exception);
        }
    }

    private String embeddingRequestId(Long documentId, Integer chunkIndex) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "doc-" + documentId + "-chunk-" + chunkIndex + "-" + suffix;
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
