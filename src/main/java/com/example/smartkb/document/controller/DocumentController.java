package com.example.smartkb.document.controller;

import com.example.smartkb.common.Result;
import com.example.smartkb.document.service.DocumentService;
import com.example.smartkb.document.vo.DocumentResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文档管理 Controller —— 上传、列表查询和删除。
 * <p>
 * 上传时校验文件类型（pdf/docx/md/txt）、MIME 和大小（≤50MB），
 * 上传到 MinIO 后异步执行 Tika 解析、语义分块和向量化入库。
 * 删除时按 Milvus → MinIO → MySQL 顺序幂等清理，支持失败重试。
 */
@Validated
@RestController
@RequestMapping("/api/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** 上传文档：校验 → MinIO → MySQL 记录 → 异步处理 */
    @PostMapping("/upload")
    public Result<Long> upload(
            @RequestParam("file") MultipartFile file,
            @Positive(message = "知识库 ID 必须为正数") @RequestParam("kbId") Long kbId) {
        return Result.success(documentService.upload(file, kbId));
    }

    /** 查询指定知识库的文档列表 */
    @GetMapping("/list")
    public Result<List<DocumentResponse>> list(
            @Positive(message = "知识库 ID 必须为正数") @RequestParam("kbId") Long kbId) {
        return Result.success(documentService.listCurrentUserDocuments(kbId));
    }

    /** 删除文档：清理 Milvus 向量 → MinIO 文件 → MySQL 记录 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Positive(message = "文档 ID 必须为正数") @PathVariable Long id) {
        documentService.deleteCurrentUserDocument(id);
        return Result.success();
    }
}
