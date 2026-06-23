package com.example.smartkb.llm.exception;

import com.example.smartkb.common.BusinessException;

/**
 * 流式输出中途中断异常 —— LLM 已在流式输出中吐出了部分 token 后发生故障。
 * <p>
 * 与 {@link AllLlmProviderFailedException} 不同，本异常表示 LLM 已经产生了
 * 部分输出给到前端，此时不允许无感知切换到备用 Provider 重试（否则会导致
 * 用户看到重复或拼接错乱的回答），只能直接终止流并提示用户重新提问。
 * <p>
 * 触发场景：
 * <ul>
 *   <li>DeepSeek 流式输出中途失败，且已向前端发出过 token</li>
 *   <li>降级到 Qwen 后，Qwen 流式输出中途失败，且已向前端发出过 token</li>
 * </ul>
 * 错误码：50302 —— 流式输出中断，请重新提问
 */
public class LlmStreamInterruptedException extends BusinessException {

    public LlmStreamInterruptedException() {
        super(50302, "AI 服务暂时不可用，请重新提问");
    }
}
