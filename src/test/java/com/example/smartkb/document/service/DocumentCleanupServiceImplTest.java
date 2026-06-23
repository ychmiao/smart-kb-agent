package com.example.smartkb.document.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.smartkb.common.BusinessException;
import com.example.smartkb.document.entity.Document;
import com.example.smartkb.document.mapper.DocumentMapper;
import com.example.smartkb.document.model.DocumentStatus;
import com.example.smartkb.document.service.impl.DocumentCleanupServiceImpl;
import com.example.smartkb.document.storage.MinioFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentCleanupServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private MinioFileStorage minioFileStorage;

    private DocumentCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new DocumentCleanupServiceImpl(documentMapper, minioFileStorage);
    }

    @Test
    void shouldListDocumentsByKbId() {
        when(documentMapper.selectList(any())).thenReturn(documents(100L));

        List<Document> docs = cleanupService.listByKbId(100L);

        assertThat(docs).hasSize(2);
        ArgumentCaptor<Wrapper<Document>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(documentMapper).selectList(captor.capture());
    }

    @Test
    void shouldReturnTrueWhenProcessingDocumentsExist() {
        when(documentMapper.selectCount(any())).thenReturn(1L);

        boolean result = cleanupService.hasProcessingDocuments(100L);

        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenNoProcessingDocuments() {
        when(documentMapper.selectCount(any())).thenReturn(0L);

        boolean result = cleanupService.hasProcessingDocuments(100L);

        assertThat(result).isFalse();
    }

    @Test
    void shouldDeleteMinioFilesForAllDocuments() {
        when(documentMapper.selectList(any())).thenReturn(documents(100L));

        cleanupService.deleteMinioFilesByKbId(100L);

        verify(minioFileStorage, times(2)).remove(any());
        verify(minioFileStorage).remove("7/100/document.pdf");
        verify(minioFileStorage).remove("7/100/notes.md");
    }

    @Test
    void shouldThrowWhenMinioDeletionFails() {
        when(documentMapper.selectList(any())).thenReturn(documents(100L));
        doThrow(new BusinessException(50014, "删除文档文件失败"))
                .when(minioFileStorage).remove("7/100/document.pdf");

        assertThatThrownBy(() -> cleanupService.deleteMinioFilesByKbId(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("删除文档文件失败");
    }

    @Test
    void shouldNotDeleteMinioWhenKbHasNoDocuments() {
        when(documentMapper.selectList(any())).thenReturn(List.of());

        cleanupService.deleteMinioFilesByKbId(200L);

        verify(minioFileStorage, never()).remove(any());
    }

    @Test
    void shouldDeleteDocumentRecordsByKbId() {
        when(documentMapper.delete(any())).thenReturn(3);

        int deleted = cleanupService.deleteDocumentRecordsByKbId(100L);

        assertThat(deleted).isEqualTo(3);
        verify(documentMapper).delete(any());
    }

    @Test
    void shouldReturnZeroWhenNoRecordsToDelete() {
        when(documentMapper.delete(any())).thenReturn(0);

        int deleted = cleanupService.deleteDocumentRecordsByKbId(999L);

        assertThat(deleted).isZero();
    }

    @Test
    void shouldSkipMinioForDocumentsWithoutMinioPath() {
        Document doc = new Document();
        doc.setId(10L);
        doc.setKbId(100L);
        doc.setStatus(DocumentStatus.FAILED.getCode());
        doc.setMinioPath(null);  // 没有 MinIO 路径
        when(documentMapper.selectList(any())).thenReturn(List.of(doc));

        cleanupService.deleteMinioFilesByKbId(100L);

        verify(minioFileStorage, never()).remove(any());
    }

    @Test
    void shouldRemoveMinioPathForDocumentsThatHaveIt() {
        Document withMinio = new Document();
        withMinio.setId(10L);
        withMinio.setKbId(100L);
        withMinio.setMinioPath("7/100/report.pdf");
        Document withoutMinio = new Document();
        withoutMinio.setId(11L);
        withoutMinio.setKbId(100L);
        withoutMinio.setMinioPath(null);
        when(documentMapper.selectList(any())).thenReturn(List.of(withMinio, withoutMinio));

        cleanupService.deleteMinioFilesByKbId(100L);

        verify(minioFileStorage, times(1)).remove("7/100/report.pdf");
    }

    private List<Document> documents(long kbId) {
        Document doc1 = new Document();
        doc1.setId(10L);
        doc1.setKbId(kbId);
        doc1.setMinioPath("7/" + kbId + "/document.pdf");
        doc1.setStatus(DocumentStatus.COMPLETED.getCode());

        Document doc2 = new Document();
        doc2.setId(11L);
        doc2.setKbId(kbId);
        doc2.setMinioPath("7/" + kbId + "/notes.md");
        doc2.setStatus(DocumentStatus.COMPLETED.getCode());

        return List.of(doc1, doc2);
    }
}
