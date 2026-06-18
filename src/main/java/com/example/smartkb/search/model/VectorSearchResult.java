package com.example.smartkb.search.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VectorSearchResult {

    private final Long docId;
    private final Integer chunkIndex;
    private final String content;
    private final String sourceText;
    private final Double score;
}

