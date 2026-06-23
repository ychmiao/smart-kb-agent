package com.example.smartkb.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.vo.ConversationResponse;
import com.example.smartkb.chat.vo.MessageResponse;

import java.util.List;

public interface ConversationService extends IService<Conversation> {

    Conversation getOrCreate(Long conversationId, Long kbId, Long userId, String question);

    List<ConversationResponse> listCurrentUserConversations(Long kbId, int page, int size);

    List<MessageResponse> listMessagesByConversation(Long conversationId, int page, int size);
}

