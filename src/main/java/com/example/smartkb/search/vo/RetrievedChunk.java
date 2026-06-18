package com.example.smartkb.search.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RetrievedChunk {

    private final Long docId;
    private final Integer chunkIndex;
    private final String content;
    private final String sourceText;
    private final Double score;
    private final String fileName;
}

