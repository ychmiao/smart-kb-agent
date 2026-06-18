package com.example.smartkb.search.service;

import com.example.smartkb.chat.model.ChatHistoryMessage;
import com.example.smartkb.search.model.QueryRewriteResult;

import java.util.List;

public interface QueryRewriteService {

    QueryRewriteResult rewrite(String question, List<ChatHistoryMessage> recentHistory);
}

