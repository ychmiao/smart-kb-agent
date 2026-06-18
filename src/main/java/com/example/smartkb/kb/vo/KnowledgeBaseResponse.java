package com.example.smartkb.kb.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class KnowledgeBaseResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final String collectionName;
    private final LocalDateTime createTime;
    private final LocalDateTime updateTime;
}

