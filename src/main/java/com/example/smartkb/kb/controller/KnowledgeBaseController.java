package com.example.smartkb.kb.controller;

import com.example.smartkb.common.Result;
import com.example.smartkb.kb.dto.CreateKnowledgeBaseRequest;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import com.example.smartkb.kb.vo.KnowledgeBaseResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库 Controller —— 创建、列表查询和删除。
 * <p>
 * 创建知识库时同步创建 Milvus collection（{@code kb_{kbId}}）。
 * 删除知识库时级联清理关联文档的 MinIO 文件、Milvus collection 和 MySQL 记录。
 * 所有接口均校验当前用户归属，禁止跨用户操作。
 */
@Validated
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** 创建知识库（含 Milvus collection 创建） */
    @PostMapping("/create")
    public Result<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.create(request));
    }

    /** 查询当前用户的所有知识库 */
    @GetMapping("/list")
    public Result<List<KnowledgeBaseResponse>> list() {
        return Result.success(knowledgeBaseService.listCurrentUserKnowledgeBases());
    }

    /** 删除知识库及关联资源（幂等重试） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Positive(message = "知识库 ID 必须为正数") @PathVariable Long id) {
        knowledgeBaseService.deleteCurrentUserKnowledgeBase(id);
        return Result.success();
    }
}

