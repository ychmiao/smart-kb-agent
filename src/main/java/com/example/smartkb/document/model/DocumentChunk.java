package com.example.smartkb.document.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DocumentChunk {

    private final Long documentId;
    private final Long kbId;
    private final Integer chunkIndex;
    private final String content;
    private final String sourceText;
}

