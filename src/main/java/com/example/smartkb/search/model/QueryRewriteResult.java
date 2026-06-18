package com.example.smartkb.search.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueryRewriteResult {

    private final Boolean needRetrieval;
    private final String rewrittenQuery;
}

