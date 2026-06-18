package com.example.smartkb.kb.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smartkb.kb.dto.CreateKnowledgeBaseRequest;
import com.example.smartkb.kb.entity.KnowledgeBase;
import com.example.smartkb.kb.vo.KnowledgeBaseResponse;

import java.util.List;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    KnowledgeBaseResponse create(CreateKnowledgeBaseRequest request);

    List<KnowledgeBaseResponse> listCurrentUserKnowledgeBases();

    KnowledgeBase requireCurrentUserKnowledgeBase(Long knowledgeBaseId);

    void deleteCurrentUserKnowledgeBase(Long knowledgeBaseId);
}
