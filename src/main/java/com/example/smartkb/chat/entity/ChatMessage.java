package com.example.smartkb.chat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String role;

    private String content;

    private String sourceDocs;

    private String rewrittenQuery;

    private Integer needRetrieval;

    private String llmProvider;

    private Integer tokenCount;

    private LocalDateTime createTime;
}

