package com.example.smartkb.search.controller;

import com.example.smartkb.common.Result;
import com.example.smartkb.search.service.RetrievalService;
import com.example.smartkb.search.vo.RetrievedChunk;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/search")
public class SearchTestController {

    private final RetrievalService retrievalService;

    public SearchTestController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/test")
    public Result<List<RetrievedChunk>> test(
            @Positive(message = "知识库 ID 必须为正数") @RequestParam("kbId") Long kbId,
            @NotBlank(message = "query 不能为空") @RequestParam("query") String query,
            @RequestParam(value = "topK", defaultValue = "5") Integer topK) {
        return Result.success(retrievalService.retrieve(kbId, query, topK));
    }
}

