package com.example.smartkb.kb.service;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.document.service.DocumentCleanupService;
import com.example.smartkb.kb.dto.CreateKnowledgeBaseRequest;
import com.example.smartkb.kb.entity.KnowledgeBase;
import com.example.smartkb.kb.mapper.KnowledgeBaseMapper;
import com.example.smartkb.kb.service.impl.KnowledgeBaseServiceImpl;
import com.example.smartkb.search.exception.VectorStoreException;
import com.example.smartkb.search.store.MilvusVectorStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceImplTest {

    @Mock
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Mock
    private MilvusVectorStore milvusVectorStore;

    @Mock
    private DocumentCleanupService documentCleanupService;

    private KnowledgeBaseService knowledgeBaseService;

    private KnowledgeBaseServiceImpl knowledgeBaseServiceImpl;

    @BeforeEach
    void setUp() {
        knowledgeBaseServiceImpl = spy(new KnowledgeBaseServiceImpl(
                milvusVectorStore, documentCleanupService));
        ReflectionTestUtils.setField(knowledgeBaseServiceImpl, "baseMapper", knowledgeBaseMapper);
        knowledgeBaseService = knowledgeBaseServiceImpl;
        UserContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCreateMilvusCollectionWhenKnowledgeBaseIsCreated() {
        whenKnowledgeBaseInsertReturns(id -> id.setId(100L));
        when(knowledgeBaseMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);
        when(knowledgeBaseMapper.selectById(100L)).thenAnswer(invocation -> savedKnowledgeBase());
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("Java 知识库");

        var response = knowledgeBaseService.create(request);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCollectionName()).isEqualTo("kb_100");
        verify(milvusVectorStore).createKnowledgeBaseCollection(100L);
    }

    @Test
    void shouldPropagateCollectionCreationFailureForTransactionRollback() {
        whenKnowledgeBaseInsertReturns(id -> id.setId(100L));
        when(knowledgeBaseMapper.updateById(any(KnowledgeBase.class))).thenReturn(1);
        doThrow(new VectorStoreException("创建失败"))
                .when(milvusVectorStore).createKnowledgeBaseCollection(100L);
        CreateKnowledgeBaseRequest request = new CreateKnowledgeBaseRequest();
        request.setName("Java 知识库");

        assertThatThrownBy(() -> knowledgeBaseService.create(request))
                .isInstanceOf(VectorStoreException.class)
                .hasMessage("创建失败");
    }

    @Test
    void shouldDeleteKnowledgeBaseSuccessfully() {
        KnowledgeBase knowledgeBase = savedKnowledgeBase();
        doReturn(knowledgeBase).when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);
        doReturn(1).when(knowledgeBaseMapper).delete(any());

        knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L);

        verify(documentCleanupService).hasProcessingDocuments(100L);
        verify(documentCleanupService).deleteMinioFilesByKbId(100L);
        verify(documentCleanupService).deleteDocumentRecordsByKbId(100L);
        verify(milvusVectorStore).dropKnowledgeBaseCollection(100L);
    }

    @Test
    void shouldExecuteCleanupInCorrectOrder() {
        KnowledgeBase knowledgeBase = savedKnowledgeBase();
        doReturn(knowledgeBase).when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);
        doReturn(1).when(knowledgeBaseMapper).delete(any());

        knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L);

        var order = inOrder(documentCleanupService, milvusVectorStore, knowledgeBaseMapper);
        order.verify(documentCleanupService).hasProcessingDocuments(100L);
        order.verify(documentCleanupService).deleteMinioFilesByKbId(100L);
        order.verify(milvusVectorStore).dropKnowledgeBaseCollection(100L);
        order.verify(documentCleanupService).deleteDocumentRecordsByKbId(100L);
        order.verify(knowledgeBaseMapper).delete(any());
    }

    @Test
    void shouldRollbackWhenMilvusDeletionFails() {
        // 注意：Milvus 失败后 MySQL 操作不会执行（因为方法中 Milvus 在 MySQL 之前执行）
        // "回滚"在此上下文指不继续执行后续 MySQL 操作
        KnowledgeBase knowledgeBase = savedKnowledgeBase();
        doReturn(knowledgeBase).when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);
        doThrow(new VectorStoreException("Milvus 删除失败"))
                .when(milvusVectorStore).dropKnowledgeBaseCollection(100L);

        assertThatThrownBy(() -> knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L))
                .isInstanceOf(VectorStoreException.class)
                .hasMessage("Milvus 删除失败");

        // MinIO 已删除，Milvus 失败，MySQL 操作不执行
        verify(documentCleanupService).deleteMinioFilesByKbId(100L);
        verify(documentCleanupService, never()).deleteDocumentRecordsByKbId(any());
        verify(knowledgeBaseMapper, never()).delete(any());
    }

    @Test
    void shouldThrowExceptionWhenMySqlDeletionFails() {
        KnowledgeBase knowledgeBase = savedKnowledgeBase();
        doReturn(knowledgeBase).when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);
        doReturn(0).when(knowledgeBaseMapper).delete(any());

        assertThatThrownBy(() -> knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库删除失败，请重试");

        // MinIO 和 Milvus 已清理，MySQL 文档已删除，仅 KB 记录删除失败
        verify(documentCleanupService).deleteMinioFilesByKbId(100L);
        verify(documentCleanupService).deleteDocumentRecordsByKbId(100L);
        verify(milvusVectorStore).dropKnowledgeBaseCollection(100L);
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonExistentKnowledgeBase() {
        doThrow(new BusinessException(40401, "知识库不存在"))
                .when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);

        assertThatThrownBy(() -> knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在");

        verify(documentCleanupService, never()).hasProcessingDocuments(any());
        verify(documentCleanupService, never()).deleteMinioFilesByKbId(any());
        verify(documentCleanupService, never()).deleteDocumentRecordsByKbId(any());
        verify(milvusVectorStore, never()).dropKnowledgeBaseCollection(any());
    }

    @Test
    void shouldRejectDeletionWhenProcessingDocumentsExist() {
        KnowledgeBase knowledgeBase = savedKnowledgeBase();
        doReturn(knowledgeBase).when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);
        when(documentCleanupService.hasProcessingDocuments(100L)).thenReturn(true);

        assertThatThrownBy(() -> knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库存在处理中的文档，请稍后再删除");

        verify(documentCleanupService, never()).deleteMinioFilesByKbId(any());
        verify(documentCleanupService, never()).deleteDocumentRecordsByKbId(any());
        verify(milvusVectorStore, never()).dropKnowledgeBaseCollection(any());
    }

    @Test
    void shouldThrowWhenMinioDeletionFailsDuringKnowledgeBaseDeletion() {
        KnowledgeBase knowledgeBase = savedKnowledgeBase();
        doReturn(knowledgeBase).when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);
        doThrow(new BusinessException(50014, "删除文档文件失败"))
                .when(documentCleanupService).deleteMinioFilesByKbId(100L);

        assertThatThrownBy(() -> knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("删除文档文件失败");

        // MinIO 失败 → Milvus 和 MySQL 不执行
        verify(documentCleanupService, never()).deleteDocumentRecordsByKbId(any());
        verify(milvusVectorStore, never()).dropKnowledgeBaseCollection(any());
        verify(knowledgeBaseMapper, never()).delete(any());
    }

    @Test
    void shouldBeIdempotentWhenDeletingAlreadyDeletedKnowledgeBase() {
        // 模拟知识库已删除（requireCurrentUserKnowledgeBase 报错）
        doThrow(new BusinessException(40401, "知识库不存在"))
                .when(knowledgeBaseServiceImpl).requireCurrentUserKnowledgeBase(100L);

        assertThatThrownBy(() -> knowledgeBaseService.deleteCurrentUserKnowledgeBase(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库不存在");

        verify(documentCleanupService, never()).hasProcessingDocuments(any());
        verify(documentCleanupService, never()).deleteMinioFilesByKbId(any());
        verify(milvusVectorStore, never()).dropKnowledgeBaseCollection(any());
        verify(knowledgeBaseMapper, never()).delete(any());
    }

    private void whenKnowledgeBaseInsertReturns(java.util.function.Consumer<KnowledgeBase> setId) {
        when(knowledgeBaseMapper.insert(any(KnowledgeBase.class))).thenAnswer(invocation -> {
            KnowledgeBase knowledgeBase = invocation.getArgument(0);
            setId.accept(knowledgeBase);
            return 1;
        });
    }

    private KnowledgeBase savedKnowledgeBase() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(100L);
        knowledgeBase.setName("Java 知识库");
        knowledgeBase.setUserId(7L);
        knowledgeBase.setCollectionName("kb_100");
        return knowledgeBase;
    }
}
