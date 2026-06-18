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

@Validated
@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/create")
    public Result<KnowledgeBaseResponse> create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return Result.success(knowledgeBaseService.create(request));
    }

    @GetMapping("/list")
    public Result<List<KnowledgeBaseResponse>> list() {
        return Result.success(knowledgeBaseService.listCurrentUserKnowledgeBases());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@Positive(message = "知识库 ID 必须为正数") @PathVariable Long id) {
        knowledgeBaseService.deleteCurrentUserKnowledgeBase(id);
        return Result.success();
    }
}

