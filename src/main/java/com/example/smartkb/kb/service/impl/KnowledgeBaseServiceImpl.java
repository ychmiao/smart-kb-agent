package com.example.smartkb.kb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.document.service.DocumentCleanupService;
import com.example.smartkb.kb.dto.CreateKnowledgeBaseRequest;
import com.example.smartkb.kb.entity.KnowledgeBase;
import com.example.smartkb.kb.mapper.KnowledgeBaseMapper;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.kb.vo.KnowledgeBaseResponse;
import com.example.smartkb.search.store.MilvusVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识库服务实现 —— 管理知识库与 Milvus collection 的生命周期。
 * <p>
 * 创建知识库时同步创建 Milvus collection（{@code kb_{kbId}}），
 * 删除时级联清理 MinIO 文件、Milvus collection 和 MySQL 记录。
 * 为消除循环依赖，文档清理通过 DocumentCleanupService 完成。
 */
@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    /** Milvus collection 命名前缀：最终名称为 kb_{知识库ID} */
    private static final String COLLECTION_NAME_PREFIX = "kb_";

    private final MilvusVectorStore milvusVectorStore;
    private final DocumentCleanupService documentCleanupService;

    public KnowledgeBaseServiceImpl(MilvusVectorStore milvusVectorStore,
                                    DocumentCleanupService documentCleanupService) {
        this.milvusVectorStore = milvusVectorStore;
        this.documentCleanupService = documentCleanupService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request) {
        Long userId = UserContext.requireUserId();

        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setName(request.getName().trim());
        knowledgeBase.setDescription(normalizeDescription(request.getDescription()));
        knowledgeBase.setUserId(userId);

        if (!save(knowledgeBase)) {
            throw new BusinessException(50001, "知识库创建失败");
        }

        knowledgeBase.setCollectionName(COLLECTION_NAME_PREFIX + knowledgeBase.getId());
        if (!updateById(knowledgeBase)) {
            throw new BusinessException(50001, "知识库创建失败");
        }
        milvusVectorStore.createKnowledgeBaseCollection(knowledgeBase.getId());

        log.info("Knowledge base created: kbId={}, userId={}, collectionName={}",
                knowledgeBase.getId(), userId, knowledgeBase.getCollectionName());
        KnowledgeBase savedKnowledgeBase = getById(knowledgeBase.getId());
        return toResponse(savedKnowledgeBase);
    }

    @Override
    public List<KnowledgeBaseResponse> listCurrentUserKnowledgeBases() {
        Long userId = UserContext.requireUserId();
        return lambdaQuery()
                .eq(KnowledgeBase::getUserId, userId)
                .orderByDesc(KnowledgeBase::getCreateTime)
                .list()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public KnowledgeBase requireCurrentUserKnowledgeBase(Long knowledgeBaseId) {
        Long userId = UserContext.requireUserId();
        KnowledgeBase knowledgeBase = lambdaQuery()
                .eq(KnowledgeBase::getId, knowledgeBaseId)
                .eq(KnowledgeBase::getUserId, userId)
                .one();
        if (knowledgeBase == null) {
            throw new BusinessException(40401, "知识库不存在");
        }
        return knowledgeBase;
    }

    /**
     * 删除知识库及关联资源。
     * <p>
     * 删除顺序（确保操作可重试）：
     * <ol>
     *   <li>校验归属</li>
     *   <li>检查处理中文档（拒绝删除）</li>
     *   <li>严格删除 MinIO 文件（失败抛异常，可重试）</li>
     *   <li>幂等删除 Milvus collection（失败抛异常，可重试）</li>
     *   <li>逻辑删除 MySQL 文档记录（@TableLogic，幂等）</li>
     *   <li>逻辑删除 MySQL 知识库记录（@TableLogic，失败返回可重试错误）</li>
     * </ol>
     * <p>
     * 注意：MySQL 删除无法回滚已完成的 MinIO/Milvus 操作，因此 MinIO/Milvus
     * 优先执行——即使后续 MySQL 失败，外部存储已处于一致状态，重试幂等。
     */
    @Override
    public void deleteCurrentUserKnowledgeBase(Long knowledgeBaseId) {
        Long userId = UserContext.requireUserId();
        requireCurrentUserKnowledgeBase(knowledgeBaseId);

        // 检查是否存在处理中的文档
        if (documentCleanupService.hasProcessingDocuments(knowledgeBaseId)) {
            throw new BusinessException(40901, "知识库存在处理中的文档，请稍后再删除");
        }

        // Step 1: 严格删除 MinIO 文件（失败抛异常，调用方获知后可重试）
        documentCleanupService.deleteMinioFilesByKbId(knowledgeBaseId);

        // Step 2: 幂等删除 Milvus collection（失败抛异常，调用方获知后可重试）
        milvusVectorStore.dropKnowledgeBaseCollection(knowledgeBaseId);

        // Step 3: 逻辑删除 MySQL 文档记录（@TableLogic，幂等：重复执行无影响）
        documentCleanupService.deleteDocumentRecordsByKbId(knowledgeBaseId);

        // Step 4: 逻辑删除知识库记录（@TableLogic，失败返回可重试错误）
        boolean deleted = lambdaUpdate()
                .eq(KnowledgeBase::getId, knowledgeBaseId)
                .eq(KnowledgeBase::getUserId, userId)
                .remove();
        if (!deleted) {
            log.warn("Knowledge base MySQL record deletion returned 0 rows: kbId={}, userId={}",
                    knowledgeBaseId, userId);
            throw new BusinessException(50002, "知识库删除失败，请重试");
        }

        log.info("Knowledge base deleted: kbId={}, userId={}", knowledgeBaseId, userId);
    }

    private KnowledgeBaseResponse toResponse(KnowledgeBase knowledgeBase) {
        return new KnowledgeBaseResponse(
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getCollectionName(),
                knowledgeBase.getCreateTime(),
                knowledgeBase.getUpdateTime()
        );
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }
}
