
package com.example.smartkb.document.service;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.service.impl.DocumentServiceImpl;
import com.example.smartkb.document.storage.MinioFileStorage;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.search.exception.VectorStoreException;
import com.example.smartkb.search.store.MilvusVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private MinioFileStorage minioFileStorage;

    @Mock
    private DocumentProcessingService documentProcessingService;

    @Mock
    private MilvusVectorStore milvusVectorStore;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        DocumentServiceImpl service = new DocumentServiceImpl(
                knowledgeBaseService,
                minioFileStorage,
                documentProcessingService,
                milvusVectorStore
        );
        ReflectionTestUtils.setField(service, "baseMapper", documentMapper);
        documentService = service;
    }

    @Test
    void shouldDeleteVectorsFileAndDatabaseRecord() {
        Document document = document(DocumentStatus.COMPLETED);
        when(documentMapper.selectById(10L)).thenReturn(document);
        when(documentMapper.deleteById(10L)).thenReturn(1);

        documentService.deleteCurrentUserDocument(10L);

        verify(knowledgeBaseService).requireCurrentUserKnowledgeBase(2L);
        org.mockito.InOrder cleanupOrder = inOrder(milvusVectorStore, minioFileStorage, documentMapper);
        cleanupOrder.verify(milvusVectorStore).deleteDocument(2L, 10L);
        cleanupOrder.verify(minioFileStorage).remove("7/2/document.pdf");
        cleanupOrder.verify(documentMapper).deleteById(10L);
    }

    @Test
    void shouldRejectDeletingDocumentWhileProcessing() {
        Document document = document(DocumentStatus.PROCESSING);
        when(documentMapper.selectById(10L)).thenReturn(document);

        assertThatThrownBy(() -> documentService.deleteCurrentUserDocument(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档正在处理中，请稍后再删除");

        verify(knowledgeBaseService).requireCurrentUserKnowledgeBase(2L);
        verify(milvusVectorStore, never()).deleteDocument(2L, 10L);
        verify(minioFileStorage, never()).remove("7/2/document.pdf");
        verify(documentMapper, never()).deleteById(10L);
    }

    @Test
    void shouldThrowExceptionWhenMilvusDeletionFails() {
        Document document = document(DocumentStatus.COMPLETED);
        when(documentMapper.selectById(10L)).thenReturn(document);
        doThrow(new VectorStoreException("Milvus 向量删除失败"))
                .when(milvusVectorStore).deleteDocument(2L, 10L);

        assertThatThrownBy(() -> documentService.deleteCurrentUserDocument(10L))
                .isInstanceOf(VectorStoreException.class)
                .hasMessage("Milvus 向量删除失败");

        verify(knowledgeBaseService).requireCurrentUserKnowledgeBase(2L);
        verify(minioFileStorage, never()).remove(any());
        verify(documentMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void shouldThrowExceptionWhenMinioDeletionFails() {
        Document document = document(DocumentStatus.COMPLETED);
        when(documentMapper.selectById(10L)).thenReturn(document);
        doThrow(new BusinessException(50014, "MinIO 文件删除失败"))
                .when(minioFileStorage).remove("7/2/document.pdf");

        assertThatThrownBy(() -> documentService.deleteCurrentUserDocument(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("MinIO 文件删除失败");

        verify(milvusVectorStore).deleteDocument(2L, 10L);
        verify(documentMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void shouldThrowExceptionWhenMySqlDeletionFailsAfterExternalCleanup() {
        Document document = document(DocumentStatus.COMPLETED);
        when(documentMapper.selectById(10L)).thenReturn(document);
        when(documentMapper.deleteById(10L)).thenReturn(0);

        assertThatThrownBy(() -> documentService.deleteCurrentUserDocument(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("文档记录删除失败，请重试");

        verify(milvusVectorStore).deleteDocument(2L, 10L);
        verify(minioFileStorage).remove("7/2/document.pdf");
    }

    @Test
    void shouldHandleConcurrentDeletionGracefully() {
        Document document = document(DocumentStatus.COMPLETED);
        Document deletedDocument = document(DocumentStatus.COMPLETED);
        deletedDocument.setIsDeleted(1);
        when(documentMapper.selectById(10L))
                .thenReturn(document)
                .thenReturn(deletedDocument);
        when(documentMapper.deleteById(10L)).thenReturn(0);

        documentService.deleteCurrentUserDocument(10L);

        verify(milvusVectorStore).deleteDocument(2L, 10L);
        verify(minioFileStorage).remove("7/2/document.pdf");
    }

    private Document document(DocumentStatus status) {
        Document document = new Document();
        document.setId(10L);
        document.setKbId(2L);
        document.setMinioPath("7/2/document.pdf");
        document.setStatus(status.getCode());
        return document;
    }
}
