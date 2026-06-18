package com.example.smartkb.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_llm_call_log")
public class LlmCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private String callType;

    private String providerName;

    private Integer isSuccess;

    private Integer latencyMs;

    private String errorMsg;

    private LocalDateTime createTime;
}

