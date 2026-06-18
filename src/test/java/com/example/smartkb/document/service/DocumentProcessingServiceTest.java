package com.example.smartkb.document.service;

import com.example.smartkb.document.chunk.SemanticChunker;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.storage.MinioFileStorage;
import org.apache.tika.Tika;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingServiceTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private MinioFileStorage minioFileStorage;

    @Mock
    private Tika tika;

    private DocumentProcessingService processingService;
    private Document document;

    @BeforeEach
    void setUp() {
        processingService = new DocumentProcessingService(
                documentMapper,
                minioFileStorage,
                tika,
                new SemanticChunker()
        );
        document = new Document();
        document.setId(1L);
        document.setKbId(2L);
        document.setMinioPath("1/2/file.txt");
        document.setStatus(DocumentStatus.PROCESSING.getCode());
        document.setChunkCount(0);
    }

    @Test
    void shouldMarkDocumentCompletedAfterParsing() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(minioFileStorage.download(document.getMinioPath())).thenReturn(inputStream);
        when(tika.parseToString(any(InputStream.class))).thenReturn("a".repeat(900));
        when(documentMapper.updateById(any(Document.class))).thenReturn(1);

        processingService.process(1L);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DocumentStatus.COMPLETED.getCode());
        assertThat(captor.getValue().getChunkCount()).isEqualTo(2);
        assertThat(captor.getValue().getErrorMsg()).isNull();
    }

    @Test
    void shouldMarkDocumentFailedWhenParsingThrowsException() throws Exception {
        InputStream inputStream = new ByteArrayInputStream("content".getBytes(StandardCharsets.UTF_8));
        when(documentMapper.selectById(1L)).thenReturn(document);
        when(minioFileStorage.download(document.getMinioPath())).thenReturn(inputStream);
        when(tika.parseToString(any(InputStream.class))).thenThrow(new IOException("broken document"));
        when(documentMapper.updateById(any(Document.class))).thenReturn(1);

        processingService.process(1L);

        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(documentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(DocumentStatus.FAILED.getCode());
        assertThat(captor.getValue().getErrorMsg()).isEqualTo("broken document");
    }
}

