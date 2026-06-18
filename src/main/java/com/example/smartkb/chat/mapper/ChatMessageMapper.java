package com.example.smartkb.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.smartkb.chat.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}

