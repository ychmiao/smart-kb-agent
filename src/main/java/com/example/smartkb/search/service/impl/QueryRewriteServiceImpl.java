package com.example.smartkb.search.service.impl;

import com.example.smartkb.chat.model.ChatHistoryMessage;
import com.example.smartkb.llm.service.LlmGatewayService;
import com.example.smartkb.search.model.QueryRewriteResult;
import com.example.smartkb.search.service.QueryRewriteService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 查询重写服务实现 —— 通过 LLM 判断检索意图并消解指代。
 * <p>
 * 功能：
 * <ul>
 *   <li>判断最新问题是否需要检索（问候/闲聊 → needRetrieval=false）</li>
 *   <li>结合最近 2 轮（4 条）对话历史消解"它、这个、刚才那个"等指代</li>
 *   <li>要求 LLM 返回 JSON，并兼容供应商偶尔附加的 Markdown 代码块</li>
 * </ul>
 */
@Slf4j
@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    /** 取最近的 4 条消息（2 轮 user+assistant 对话）作为上下文 */
    private static final int RECENT_HISTORY_MESSAGE_COUNT = 4;

    private final LlmGatewayService llmGatewayService;
    private final ObjectMapper objectMapper;

    public QueryRewriteServiceImpl(LlmGatewayService llmGatewayService, ObjectMapper objectMapper) {
        this.llmGatewayService = llmGatewayService;
        this.objectMapper = objectMapper;
    }

    @Override
    public QueryRewriteResult rewrite(String question, List<ChatHistoryMessage> recentHistory) {
        String normalizedQuestion = question.strip();
        try {
            String requestId = "rewrite-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            String response = llmGatewayService.chat(
                    requestId,
                    buildPrompt(normalizedQuestion, recentHistory)
            );
            return parseResponse(response);
        } catch (RuntimeException exception) {
            log.warn("Query rewrite failed, falling back to original question: errorType={}",
                    exception.getClass().getSimpleName());
            return fallback(normalizedQuestion);
        }
    }

    private QueryRewriteResult parseResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(json);
            JsonNode needRetrievalNode = root.get("needRetrieval");
            JsonNode rewrittenQueryNode = root.get("rewrittenQuery");
            if (!root.isObject()
                    || root.size() != 2
                    || needRetrievalNode == null
                    || !needRetrievalNode.isBoolean()
                    || rewrittenQueryNode == null
                    || !rewrittenQueryNode.isTextual()
                    || rewrittenQueryNode.asText().isBlank()) {
                throw new IllegalArgumentException("查询改写响应字段无效");
            }
            return new QueryRewriteResult(
                    needRetrievalNode.booleanValue(),
                    rewrittenQueryNode.asText().strip()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("查询改写响应不是有效 JSON", exception);
        }
    }

    private String extractJson(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("查询改写响应为空");
        }
        String normalized = response.strip();
        int objectStart = normalized.indexOf('{');
        int objectEnd = normalized.lastIndexOf('}');
        if (objectStart < 0 || objectEnd < objectStart) {
            throw new IllegalArgumentException("查询改写响应不包含 JSON 对象");
        }
        return normalized.substring(objectStart, objectEnd + 1);
    }

    private String buildPrompt(String question, List<ChatHistoryMessage> recentHistory) {
        List<ChatHistoryMessage> safeHistory = recentHistory == null ? List.of() : recentHistory;
        int fromIndex = Math.max(0, safeHistory.size() - RECENT_HISTORY_MESSAGE_COUNT);
        StringBuilder historyText = new StringBuilder();
        for (ChatHistoryMessage message : safeHistory.subList(fromIndex, safeHistory.size())) {
            historyText.append(message.role())
                    .append("：")
                    .append(message.content())
                    .append('\n');
        }

        return """
                你是一个查询理解助手，需要完成两个任务：
                1. 判断用户最新问题是否需要检索知识库才能回答；
                2. 如果需要检索，请结合历史对话将问题改写为语义完整、适合向量检索的独立问题。

                历史对话：
                %s
                用户最新问题：
                %s

                判断规则：
                - 当前场景是知识库问答。专业知识、事实、规则、制度、产品、业务和技术问题默认 needRetrieval=true，不要求用户明确说“知识库”或“文档”；
                - 只有明显与知识库资料无关的问候、感谢、闲聊、生活建议、简单计算或创作请求，needRetrieval=false；
                - 无法确定是否与知识库相关时，优先 needRetrieval=true，避免漏掉文档中的答案；
                - 包含“它、这个、刚才那个、上面说的”等指代词时，需要结合历史补全；
                - 问题本身清晰完整时，rewrittenQuery 直接使用原问题。

                示例：
                - “一天吃几顿饭合适”是通用生活问题，needRetrieval=false；
                - “试用期多久”虽然没有提到文档，但属于制度事实问题，needRetrieval=true；
                - “员工手册规定一天工作几小时”询问私有文档，needRetrieval=true；

                请严格按照 JSON 格式输出，不要输出 Markdown，不要输出解释：
                {
                  "needRetrieval": true,
                  "rewrittenQuery": "改写后的问题"
                }
                """.formatted(historyText, question);
    }

    private QueryRewriteResult fallback(String question) {
        return new QueryRewriteResult(true, question);
    }
}
