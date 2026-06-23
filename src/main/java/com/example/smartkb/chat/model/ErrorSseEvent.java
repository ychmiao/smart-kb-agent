package com.example.smartkb.chat.model;

/**
 * SSE error 事件 —— 流式响应异常通知。
 * <p>
 * type: "error"<br>
 * message: 安全错误描述（不泄露内部细节），客户端收到后等待后续 done 事件结束连接。
 * 三种错误类型：
 * <ul>
 *   <li>AllLlmProviderFailedException → "AI 服务暂时不可用，请稍后再试"</li>
 *   <li>BusinessException → 业务描述消息</li>
 *   <li>其他异常 → "服务处理异常，请稍后重试"</li>
 * </ul>
 */
public record ErrorSseEvent(String type, String message) {

    public ErrorSseEvent(String message) {
        this("error", message);
    }
}

