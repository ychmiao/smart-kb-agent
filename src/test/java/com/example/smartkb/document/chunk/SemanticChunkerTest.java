package com.example.smartkb.document.chunk;

import com.example.smartkb.document.model.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}

