package com.example.smartkb.document.chunk;

import com.example.smartkb.document.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SemanticChunkerTest {

    private final SemanticChunker semanticChunker = new SemanticChunker();

    @Test
    void shouldLimitChunkLengthAndKeepOverlap() {
        String text = "a".repeat(1200);

        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, text);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).allMatch(chunk -> chunk.getContent().length() <= 500);
        assertThat(chunks.get(0).getContent().substring(400))
                .isEqualTo(chunks.get(1).getContent().substring(0, 100));
        assertThat(chunks.get(1).getContent().substring(400))
                .isEqualTo(chunks.get(2).getContent().substring(0, 100));
    }

    @Test
    void shouldPreferParagraphBoundary() {
        String firstParagraph = "甲".repeat(300);
        String secondParagraph = "乙".repeat(300);

        List<DocumentChunk> chunks = semanticChunker.chunk(
                1L,
                2L,
                firstParagraph + "\n\n" + secondParagraph
        );

        assertThat(chunks.get(0).getContent()).isEqualTo(firstParagraph);
        assertThat(chunks.get(1).getContent()).startsWith("甲".repeat(100));
    }

    @Test
    void shouldSplitAtChineseSentenceBoundary() {
        String partA = "甲".repeat(300);
        String partB = "乙".repeat(200);
        String text = partA + "。" + partB;

        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, text);

        assertThat(chunks.get(0).getContent()).endsWith("。");
        assertThat(chunks.get(0).getContent()).isEqualTo(partA + "。");
        assertThat(chunks).hasSize(2);
    }

    @Test
    void shouldSplitAtEnglishSentenceBoundary() {
        String partA = "a".repeat(300);
        String partB = "b".repeat(200);
        String text = partA + ". " + partB;

        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, text);

        assertThat(chunks.get(0).getContent()).endsWith(".");
        assertThat(chunks.get(0).getContent()).isEqualTo(partA + ".");
        assertThat(chunks).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListForEmptyText() {
        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, "");

        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldReturnEmptyListForBlankText() {
        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, "   \n\n  ");

        assertThat(chunks).isEmpty();
    }

    @Test
    void shouldTruncateSourceTextToFiftyChars() {
        String text = "x".repeat(100);

        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getSourceText()).hasSize(50);
    }

    @Test
    void shouldReturnSingleChunkForShortText() {
        List<DocumentChunk> chunks = semanticChunker.chunk(1L, 2L, "short text");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getContent()).isEqualTo("short text");
        assertThat(chunks.get(0).getChunkIndex()).isZero();
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatThrownBy(() -> new SemanticChunker(0, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunker(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

