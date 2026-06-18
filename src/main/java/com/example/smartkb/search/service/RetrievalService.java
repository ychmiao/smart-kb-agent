package com.example.smartkb.search.service;

import com.example.smartkb.search.vo.RetrievedChunk;

import java.util.List;

public interface RetrievalService {

    List<RetrievedChunk> retrieve(Long kbId, String query, Integer topK);
}

