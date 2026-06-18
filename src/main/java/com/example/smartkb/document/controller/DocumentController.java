package com.example.smartkb.document.controller;

import com.example.smartkb.common.Result;
import com.example.smartkb.document.service.DocumentService;
import com.example.smartkb.document.vo.DocumentResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public Result<Long> upload(
            @RequestParam("file") MultipartFile file,
            @Positive(message = "知识库 ID 必须为正数") @RequestParam("kbId") Long kbId) {
        return Result.success(documentService.upload(file, kbId));
    }

    @GetMapping("/list")
    public Result<List<DocumentResponse>> list(
            @Positive(message = "知识库 ID 必须为正数") @RequestParam("kbId") Long kbId) {
        return Result.success(documentService.listCurrentUserDocuments(kbId));
    }
}

