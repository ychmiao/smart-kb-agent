package com.example.smartkb.document.chunk;

import com.example.smartkb.document.model.DocumentChunk;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义分块器 —— 将文档文本按语义边界切分为适合向量化的文本块。
 * <p>
 * 分块策略（优先级递减）：
 * <ol>
 *   <li>优先按段落（连续两个换行）分割</li>
 *   <li>段落过长时按句末标点（。！？.!?）分割</li>
 *   <li>仍超长时在最大长度处硬截断</li>
 * </ol>
 * 每块默认 500 字，相邻块间 100 字重叠，保留前 50 字作为引用摘要（sourceText）。
 */
@Component
public class SemanticChunker {

    /** 每个 chunk 的最大字符数 */
    private static final int DEFAULT_MAX_CHUNK_LENGTH = 500;
    /** 相邻 chunk 间的重叠字符数 */
    private static final int DEFAULT_OVERLAP_LENGTH = 100;
    /** sourceText（引用摘要）截取长度 */
    private static final int SOURCE_TEXT_LENGTH = 50;
    /** 开始寻找语义边界的最小偏移量，避免边界出现在 chunk 开头过近的位置 */
    private static final int MIN_SEMANTIC_BREAK_POSITION = 250;

    private final int maxChunkLength;
    private final int overlapLength;

    public SemanticChunker() {
        this(DEFAULT_MAX_CHUNK_LENGTH, DEFAULT_OVERLAP_LENGTH);
    }

    SemanticChunker(int maxChunkLength, int overlapLength) {
        if (maxChunkLength <= 0 || overlapLength < 0 || overlapLength >= maxChunkLength) {
            throw new IllegalArgumentException("Invalid chunk length configuration");
        }
        this.maxChunkLength = maxChunkLength;
        this.overlapLength = overlapLength;
    }

    public List<DocumentChunk> chunk(Long documentId, Long kbId, String text) {
        String normalizedText = normalize(text);
        if (normalizedText.isBlank()) {
            return List.of();
        }

        List<DocumentChunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalizedText.length()) {
            int targetEnd = Math.min(start + maxChunkLength, normalizedText.length());
            int end = targetEnd;
            if (targetEnd < normalizedText.length()) {
                end = findSemanticBoundary(normalizedText, start, targetEnd);
            }

            String content = normalizedText.substring(start, end).strip();
            if (!content.isEmpty()) {
                chunks.add(new DocumentChunk(
                        documentId,
                        kbId,
                        chunks.size(),
                        content,
                        content.substring(0, Math.min(SOURCE_TEXT_LENGTH, content.length()))
                ));
            }
            if (end >= normalizedText.length()) {
                break;
            }

            int nextStart = Math.max(0, end - overlapLength);
            while (nextStart < end && Character.isWhitespace(normalizedText.charAt(nextStart))) {
                nextStart++;
            }
            start = nextStart >= end ? end : nextStart;
        }
        return chunks;
    }

    private int findSemanticBoundary(String text, int start, int targetEnd) {
        int minimumBreak = Math.min(start + MIN_SEMANTIC_BREAK_POSITION, targetEnd);
        int paragraphBreak = text.lastIndexOf("\n\n", targetEnd - 1);
        if (paragraphBreak >= minimumBreak) {
            return paragraphBreak;
        }

        for (int index = targetEnd - 1; index >= minimumBreak; index--) {
            if (isSentenceEnding(text.charAt(index))) {
                return index + 1;
            }
        }
        return targetEnd;
    }

    private boolean isSentenceEnding(char character) {
        return character == '。'
                || character == '！'
                || character == '？'
                || character == '.'
                || character == '!'
                || character == '?';
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }
}

