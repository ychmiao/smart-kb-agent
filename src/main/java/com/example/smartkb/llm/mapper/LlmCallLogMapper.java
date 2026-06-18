package com.example.smartkb.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.smartkb.llm.entity.LlmCallLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmCallLogMapper extends BaseMapper<LlmCallLog> {
}

