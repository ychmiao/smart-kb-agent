package com.example.smartkb.document.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class DocumentResponse {

    private final Long id;
    private final Long kbId;
    private final String fileName;
    private final String fileType;
    private final Long fileSize;
    private final Integer chunkCount;
    private final Integer status;
    private final String errorMsg;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;
}

