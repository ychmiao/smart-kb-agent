package com.example.smartkb.chat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.smartkb.chat.entity.Conversation;

public interface ConversationService extends IService<Conversation> {

    Conversation getOrCreate(Long conversationId, Long kbId, Long userId, String question);
}

