package com.example.smartkb.llm.exception;

import com.example.smartkb.common.BusinessException;

public class AllLlmProviderFailedException extends BusinessException {

    public AllLlmProviderFailedException(Throwable cause) {
        super(50301, "AI 服务暂时不可用，请稍后再试", cause);
    }
}

