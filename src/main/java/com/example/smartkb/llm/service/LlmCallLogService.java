package com.example.smartkb.llm.service;

import com.example.smartkb.llm.entity.LlmCallLog;
import com.example.smartkb.llm.mapper.LlmCallLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class LlmCallLogService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final LlmCallLogMapper llmCallLogMapper;

    public LlmCallLogService(LlmCallLogMapper llmCallLogMapper) {
        this.llmCallLogMapper = llmCallLogMapper;
    }

    @Async("llmLogTaskExecutor")
    public void recordAsync(String requestId, String callType, String providerName,
                            boolean success, long latencyMillis, String errorMessage) {
        try {
            LlmCallLog callLog = new LlmCallLog();
            callLog.setRequestId(requestId);
            callLog.setCallType(callType);
            callLog.setProviderName(providerName);
            callLog.setIsSuccess(success ? 1 : 0);
            callLog.setLatencyMs((int) Math.min(Integer.MAX_VALUE, Math.max(0L, latencyMillis)));
            callLog.setErrorMsg(sanitizeErrorMessage(errorMessage));
            llmCallLogMapper.insert(callLog);
        } catch (RuntimeException exception) {
            log.error("Failed to persist LLM call log: requestId={}, callType={}, provider={}",
                    requestId, callType, providerName, exception);
        }
    }

    private String sanitizeErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        String sanitized = errorMessage.replace('\r', ' ').replace('\n', ' ').trim();
        return sanitized.substring(0, Math.min(MAX_ERROR_MESSAGE_LENGTH, sanitized.length()));
    }
}

