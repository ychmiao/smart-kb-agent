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

/**
 * 检索服务实现 —— 查询嵌入 → Milvus 向量检索 → 补充文档名。
 * <p>
 * 调用链路：
 * <ol>
 *   <li>校验知识库归属</li>
 *   <li>调用 LlmGatewayService.embedding() 将查询转为向量</li>
 *   <li>在 kb_{kbId} collection 中执行 COSINE 相似度搜索</li>
 *   <li>通过 DocumentService 批量查询文档名称</li>
 *   <li>组装 RetrievedChunk 返回（含 score、content、fileName）</li>
 * </ol>
 */
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

