package com.example.smartkb.chat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.mapper.ConversationMapper;
import com.example.smartkb.chat.service.ConversationService;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    private static final int MAX_TITLE_LENGTH = 200;

    private final KnowledgeBaseService knowledgeBaseService;

    public ConversationServiceImpl(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation getOrCreate(Long conversationId, Long kbId, Long userId, String question) {
        knowledgeBaseService.requireCurrentUserKnowledgeBase(kbId);
        if (conversationId == null) {
            Conversation conversation = new Conversation();
            conversation.setKbId(kbId);
            conversation.setUserId(userId);
            String title = question.strip();
            conversation.setTitle(title.substring(0, Math.min(MAX_TITLE_LENGTH, title.length())));
            if (!save(conversation)) {
                throw new BusinessException(50020, "会话创建失败");
            }
            return conversation;
        }

        Conversation conversation = lambdaQuery()
                .eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, userId)
                .one();
        if (conversation == null || !kbId.equals(conversation.getKbId())) {
            throw new BusinessException(40420, "会话不存在");
        }
        return conversation;
    }
}

