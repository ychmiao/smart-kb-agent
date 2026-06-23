package com.example.smartkb.search.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 查询重写结果 —— 由 QueryRewriteService 返回。
 * <p>
 * needRetrieval 表示当前问题是否需要检索知识库（false 表示问候/闲聊等无需检索的场景）。
 * rewrittenQuery 是消解指代后的独立检索语句（无需改写时等于原问题）。
 */
@Getter
@AllArgsConstructor
public class QueryRewriteResult {

    /** 是否需要检索知识库 */
    private final Boolean needRetrieval;
    /** 改写后的检索查询 */
    private final String rewrittenQuery;
}

