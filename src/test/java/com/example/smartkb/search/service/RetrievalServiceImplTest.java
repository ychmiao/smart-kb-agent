package com.example.smartkb.search.service;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.document.service.DocumentService;
import com.example.smartkb.kb.entity.KnowledgeBase;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.model.VectorSearchResult;
import com.example.smartkb.search.service.impl.RetrievalServiceImpl;
import com.example.smartkb.search.store.MilvusVectorStore;
import com.example.smartkb.search.vo.RetrievedChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceImplTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private LlmGatewayService llmGatewayService;

    @Mock
    private MilvusVectorStore milvusVectorStore;

    @Mock
    private DocumentService documentService;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalServiceImpl(
                knowledgeBaseService,
                llmGatewayService,
                milvusVectorStore,
                documentService
        );
    }

    @Test
    void shouldRetrieveChunksAndFillFileNames() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(1L);
        List<Double> embedding = List.of(0.1, 0.2);
        List<VectorSearchResult> vectorResults = List.of(
                new VectorSearchResult(10L, 0, "content-a", "source-a", 0.95),
                new VectorSearchResult(11L, 2, "content-b", "source-b", 0.87)
        );
        when(knowledgeBaseService.requireCurrentUserKnowledgeBase(1L)).thenReturn(knowledgeBase);
        when(llmGatewayService.embedding(anyString(), org.mockito.ArgumentMatchers.eq("question")))
                .thenReturn(embedding);
        when(milvusVectorStore.search(1L, embedding, 5)).thenReturn(vectorResults);
        when(documentService.getFileNames(1L, Set.of(10L, 11L)))
                .thenReturn(Map.of(10L, "a.pdf", 11L, "b.docx"));

        List<RetrievedChunk> results = retrievalService.retrieve(1L, "question", 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getFileName()).isEqualTo("a.pdf");
        assertThat(results.get(0).getScore()).isEqualTo(0.95);
        assertThat(results.get(1).getFileName()).isEqualTo("b.docx");
        verify(knowledgeBaseService).requireCurrentUserKnowledgeBase(1L);
        verify(milvusVectorStore).search(1L, embedding, 5);
    }

    @Test
    void shouldReturnEmptyListWhenVectorSearchHasNoResults() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(2L);
        List<Double> embedding = List.of(0.1, 0.2);
        when(knowledgeBaseService.requireCurrentUserKnowledgeBase(2L)).thenReturn(knowledgeBase);
        when(llmGatewayService.embedding(anyString(), org.mockito.ArgumentMatchers.eq("empty query"))).thenReturn(embedding);
        when(milvusVectorStore.search(2L, embedding, 5)).thenReturn(List.of());

        List<RetrievedChunk> results = retrievalService.retrieve(2L, "empty query", 5);

        assertThat(results).isEmpty();
    }

    @Test
    void shouldHandleMissingFileNamesGracefully() {
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(3L);
        List<Double> embedding = List.of(0.1, 0.2);
        List<VectorSearchResult> vectorResults = List.of(
                new VectorSearchResult(99L, 0, "content", "source", 0.8)
        );
        when(knowledgeBaseService.requireCurrentUserKnowledgeBase(3L)).thenReturn(knowledgeBase);
        when(llmGatewayService.embedding(anyString(), org.mockito.ArgumentMatchers.eq("orphan doc"))).thenReturn(embedding);
        when(milvusVectorStore.search(3L, embedding, 5)).thenReturn(vectorResults);
        when(documentService.getFileNames(3L, Set.of(99L))).thenReturn(Map.of());

        List<RetrievedChunk> results = retrievalService.retrieve(3L, "orphan doc", 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getFileName()).isNull();
    }

    @Test
    void shouldThrowWhenKnowledgeBaseNotOwnedByCurrentUser() {
        when(knowledgeBaseService.requireCurrentUserKnowledgeBase(999L))
                .thenThrow(new BusinessException(40301, "知识库访问权限不足"));

        assertThatThrownBy(() -> retrievalService.retrieve(999L, "query", 5))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识库访问权限不足");
    }
}

