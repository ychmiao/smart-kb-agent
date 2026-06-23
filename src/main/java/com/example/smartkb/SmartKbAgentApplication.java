package com.example.smartkb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Smart KB Agent —— 基于 RAG 架构的智能知识库问答系统。
 * <p>
 * 采用 Spring Boot 单体模块化架构，按业务领域分包。
 * 核心功能：文档上传解析、语义分块、向量化入库、语义检索、
 * 多轮对话、查询重写、多模型熔断降级、SSE 流式输出。
 */
@SpringBootApplication
public class SmartKbAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartKbAgentApplication.class, args);
    }
}

