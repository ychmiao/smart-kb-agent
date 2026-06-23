package com.example.smartkb.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.smartkb.chat.entity.ChatMessage;
import com.example.smartkb.chat.entity.Conversation;
import com.example.smartkb.chat.mapper.ChatMessageMapper;
import com.example.smartkb.chat.mapper.ConversationMapper;
import com.example.smartkb.chat.service.impl.ConversationServiceImpl;
import com.example.smartkb.chat.vo.ConversationResponse;
import com.example.smartkb.chat.vo.MessageResponse;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.UserContext;
import com.example.smartkb.kb.service.KnowledgeBaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private ConversationMapper conversationMapper;

    @Mock
    private ChatMessageMapper chatMessageMapper;

    private ConversationServiceImpl conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationServiceImpl(knowledgeBaseService, chatMessageMapper);
        ReflectionTestUtils.setField(conversationService, "baseMapper", conversationMapper);
        UserContext.setUserId(7L);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldListCurrentUserConversations() {
        Conversation conv = new Conversation();
        conv.setId(10L);
        conv.setKbId(1L);
        conv.setUserId(7L);
        conv.setTitle("Test conversation");
        conv.setCreateTime(LocalDateTime.now());
        conv.setUpdateTime(LocalDateTime.now());

        Page<Conversation> pageResult = new Page<>(1, 20, 1);
        pageResult.setRecords(List.of(conv));
        when(conversationMapper.selectPage(any(Page.class), ArgumentMatchers.<LambdaQueryWrapper<Conversation>>any()))
                .thenReturn(pageResult);

        List<ConversationResponse> result = conversationService.listCurrentUserConversations(1L, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(10L);
        assertThat(result.get(0).title()).isEqualTo("Test conversation");
    }

    @Test
    void shouldReturnEmptyConversationListForNoData() {
        Page<Conversation> emptyPage = new Page<>(1, 20, 0);
        emptyPage.setRecords(List.of());
        when(conversationMapper.selectPage(any(Page.class), ArgumentMatchers.<LambdaQueryWrapper<Conversation>>any()))
                .thenReturn(emptyPage);

        List<ConversationResponse> result = conversationService.listCurrentUserConversations(1L, 1, 20);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldListMessagesByConversation() {
        when(conversationMapper.selectCount(ArgumentMatchers.<LambdaQueryWrapper<Conversation>>any()))
                .thenReturn(1L);

        ChatMessage msg = new ChatMessage();
        msg.setId(100L);
        msg.setConversationId(20L);
        msg.setRole("user");
        msg.setContent("你好");
        msg.setNeedRetrieval(0);
        msg.setCreateTime(LocalDateTime.now());

        Page<ChatMessage> pageResult = new Page<>(1, 20, 1);
        pageResult.setRecords(List.of(msg));
        when(chatMessageMapper.selectPage(any(Page.class), ArgumentMatchers.<LambdaQueryWrapper<ChatMessage>>any()))
                .thenReturn(pageResult);

        List<MessageResponse> result = conversationService.listMessagesByConversation(20L, 1, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).role()).isEqualTo("user");
        assertThat(result.get(0).content()).isEqualTo("你好");
    }

    @Test
    void shouldThrowWhenConversationNotOwnedByCurrentUser() {
        when(conversationMapper.selectCount(ArgumentMatchers.<LambdaQueryWrapper<Conversation>>any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> conversationService.listMessagesByConversation(999L, 1, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("会话不存在");
    }
}
