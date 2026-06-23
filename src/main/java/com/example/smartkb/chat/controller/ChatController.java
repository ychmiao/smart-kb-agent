package com.example.smartkb.chat.controller;

import com.example.smartkb.chat.dto.ChatStreamRequest;
import com.example.smartkb.chat.service.ChatService;
import com.example.smartkb.chat.service.ConversationService;
import com.example.smartkb.chat.vo.ConversationResponse;
import com.example.smartkb.chat.vo.MessageResponse;
import com.example.smartkb.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 对话模块 Controller —— SSE 流式问答与会话历史查询。
 * <p>
 * <strong>SSE 事件流（POST /api/chat/stream）：</strong>
 * {@code rewrite → (token*) → sources → done}
 * 异常时返回 {@code error → done}，保证连接正常结束。
 * <p>
 * <strong>会话历史（GET）：</strong>
 * 支持按知识库查询会话列表和按会话查询消息历史，均分页并校验归属。
 */
@Validated
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;

    public ChatController(ChatService chatService, ConversationService conversationService) {
        this.chatService = chatService;
        this.conversationService = conversationService;
    }

    /**
     * SSE 流式问答接口。
     * 请求体支持 conversationId（为空则自动创建新会话）、kbId 和 question。
     * 响应为 text/event-stream 格式，包含 rewrite、token、sources 和 done 事件。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Valid @RequestBody ChatStreamRequest request) {
        return chatService.stream(request);
    }

    /** 查询当前用户在指定知识库下的会话列表（分页 + 归属校验） */
    @GetMapping("/conversations")
    public Result<List<ConversationResponse>> conversations(
            @Positive(message = "知识库 ID 必须为正数") @RequestParam("kbId") Long kbId,
            @PositiveOrZero(message = "页码必须非负") @RequestParam(value = "page", defaultValue = "1") int page,
            @Positive(message = "每页条数必须为正数") @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(conversationService.listCurrentUserConversations(kbId, page, size));
    }

    /** 查询指定会话的消息历史（分页 + 归属校验） */
    @GetMapping("/messages/{conversationId}")
    public Result<List<MessageResponse>> messages(
            @Positive(message = "会话 ID 必须为正数") @PathVariable Long conversationId,
            @PositiveOrZero(message = "页码必须非负") @RequestParam(value = "page", defaultValue = "1") int page,
            @Positive(message = "每页条数必须为正数") @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(conversationService.listMessagesByConversation(conversationId, page, size));
    }
}

