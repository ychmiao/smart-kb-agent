package com.example.smartkb.llm.service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型统一网关接口 —— 业务代码唯一的大模型调用入口。
 * <p>
 * 业务模块只依赖此接口，不直接调用 DeepSeek 或 Qwen 的 API。
 * 网关内部负责供应商路由、熔断降级和调用日志记录。
 */
public interface LlmGatewayService {

    /** 同步 Chat 调用，优先 DeepSeek，失败降级 Qwen */
    String chat(String requestId, String prompt);

    /** 流式 Chat 调用，返回 token 流 */
    Flux<String> streamChat(String requestId, String prompt);

    /**
     * 流式 Chat 调用，附带供应商回调。
     * 当成功获取到首个 token 时通过 callback 告知实际使用的供应商。
     */
    Flux<String> streamChat(String requestId, String prompt, Consumer<String> providerCallback);

    /** Embedding 调用，固定路由到 Qwen text-embedding-v3 */
    List<Double> embedding(String requestId, String text);
}

