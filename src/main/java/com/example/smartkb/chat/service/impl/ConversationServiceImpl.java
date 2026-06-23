package com.example.smartkb.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.smartkb.chat.entity.ChatMessage;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.mapper.ChatMessageMapper;
import com.example.smartkb.chat.mapper.ConversationMapper;
import com.example.smartkb.chat.service.ConversationService;
import com.example.smartkb.chat.vo.ConversationResponse;
import com.example.smartkb.chat.vo.MessageResponse;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话服务实现 —— 会话的获取/创建、列表查询和消息历史查询。
 * <p>
 * 流式问答入口调用 {@code getOrCreate()} —— 传入 null 时自动创建新会话，
 * 已有会话时校验 userId 和 kbId 是否一致。
 * 历史查询均分页并做归属校验，防止越权访问。
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation>
        implements ConversationService {

    /** 会话标题最大长度 */
    private static final int MAX_TITLE_LENGTH = 200;
    /** 默认分页大小 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    /** 分页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;

    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatMessageMapper chatMessageMapper;

    public ConversationServiceImpl(KnowledgeBaseService knowledgeBaseService,
                                   ChatMessageMapper chatMessageMapper) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.chatMessageMapper = chatMessageMapper;
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

    @Override
    public List<ConversationResponse> listCurrentUserConversations(Long kbId, int page, int size) {
        Long userId = UserContext.requireUserId();
        knowledgeBaseService.requireCurrentUserKnowledgeBase(kbId);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNum = Math.max(page, 1);

        LambdaQueryWrapper<Conversation> query = Wrappers.lambdaQuery();
        query.eq(Conversation::getUserId, userId)
                .eq(Conversation::getKbId, kbId)
                .orderByDesc(Conversation::getUpdateTime);
        Page<Conversation> result = baseMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return result.getRecords().stream()
                .map(c -> new ConversationResponse(
                        c.getId(),
                        c.getKbId(),
                        c.getTitle(),
                        c.getCreateTime(),
                        c.getUpdateTime()
                ))
                .toList();
    }

    @Override
    public List<MessageResponse> listMessagesByConversation(Long conversationId, int page, int size) {
        Long userId = UserContext.requireUserId();
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNum = Math.max(page, 1);

        LambdaQueryWrapper<Conversation> convQuery = Wrappers.lambdaQuery();
        convQuery.eq(Conversation::getId, conversationId)
                .eq(Conversation::getUserId, userId);
        if (baseMapper.selectCount(convQuery) == 0) {
            throw new BusinessException(40420, "会话不存在");
        }

        LambdaQueryWrapper<ChatMessage> query = Wrappers.lambdaQuery();
        query.eq(ChatMessage::getConversationId, conversationId)
                .orderByAsc(ChatMessage::getCreateTime);

        Page<ChatMessage> result = chatMessageMapper.selectPage(
                new Page<>(pageNum, pageSize), query);

        return result.getRecords().stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getRole(),
                        m.getContent(),
                        m.getRewrittenQuery(),
                        m.getNeedRetrieval() != null && m.getNeedRetrieval() == 1,
                        m.getLlmProvider(),
                        m.getCreateTime()
                ))
                .toList();
    }
}

