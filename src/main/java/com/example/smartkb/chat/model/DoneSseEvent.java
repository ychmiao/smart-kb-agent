package com.example.smartkb.chat.model;

/**
 * SSE done 事件 —— 流式响应结束标记。
 * <p>
 * type: "done"<br>
 * 客户端收到此事件后应关闭 SSE 连接。
 */
public record DoneSseEvent(String type) {

    public DoneSseEvent() {
        this("done");
    }
}

