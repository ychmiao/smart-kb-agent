package com.example.smartkb.search.service.impl;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.document.service.DocumentService;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.model.VectorSearchResult;
import com.example.smartkb.search.service.RetrievalService;
import com.example.smartkb.search.store.MilvusVectorStore;
import com.example.smartkb.search.vo.RetrievedChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 100;

    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmGatewayService llmGatewayService;
    private final MilvusVectorStore milvusVectorStore;
    private final DocumentService documentService;

    public RetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                LlmGatewayService llmGatewayService,
                                MilvusVectorStore milvusVectorStore,
                                DocumentService documentService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.llmGatewayService = llmGatewayService;
        this.milvusVectorStore = milvusVectorStore;
        this.documentService = documentService;
    }

    @Override
    public List<RetrievedChunk> retrieve(Long kbId, String query, Integer topK) {
        knowledgeBaseService.requireCurrentUserKnowledgeBase(kbId);
        if (query == null || query.isBlank()) {
            throw new BusinessException(40030, "query 不能为空");
        }
        int resultLimit = topK == null ? DEFAULT_TOP_K : topK;
        if (resultLimit < 1 || resultLimit > MAX_TOP_K) {
            throw new BusinessException(40031, "topK 必须在 1 到 100 之间");
        }

        String requestId = "search-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        List<Double> queryEmbedding = llmGatewayService.embedding(requestId, query.trim());
        List<VectorSearchResult> searchResults = milvusVectorStore.search(kbId, queryEmbedding, resultLimit);
        Set<Long> documentIds = searchResults.stream()
                .map(VectorSearchResult::getDocId)
                .collect(Collectors.toSet());
        Map<Long, String> fileNames = documentService.getFileNames(kbId, documentIds);

        return searchResults.stream()
                .map(result -> new RetrievedChunk(
                        result.getDocId(),
                        result.getChunkIndex(),
                        result.getContent(),
                        result.getSourceText(),
                        result.getScore(),
                        fileNames.get(result.getDocId())
                ))
                .toList();
    }
}

