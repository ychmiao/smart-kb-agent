package com.example.smartkb.kb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.kb.dto.CreateKnowledgeBaseRequest;
import com.example.smartkb.kb.entity.KnowledgeBase;
import com.example.smartkb.kb.mapper.KnowledgeBaseMapper;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.kb.vo.KnowledgeBaseResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseService {

    private static final String COLLECTION_NAME_PREFIX = "kb_";

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentUserKnowledgeBase(Long knowledgeBaseId) {
        Long userId = UserContext.requireUserId();
        requireCurrentUserKnowledgeBase(knowledgeBaseId);

        boolean deleted = lambdaUpdate()
                .eq(KnowledgeBase::getId, knowledgeBaseId)
                .eq(KnowledgeBase::getUserId, userId)
                .remove();
        if (!deleted) {
            throw new BusinessException(50002, "知识库删除失败");
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
