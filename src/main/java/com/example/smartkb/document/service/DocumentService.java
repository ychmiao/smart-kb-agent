package com.example.smartkb.document.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.vo.DocumentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService extends IService<Document> {

    Long upload(MultipartFile file, Long knowledgeBaseId);

    List<DocumentResponse> listCurrentUserDocuments(Long knowledgeBaseId);
}

