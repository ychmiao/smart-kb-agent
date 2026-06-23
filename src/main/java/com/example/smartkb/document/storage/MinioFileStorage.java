package com.example.smartkb.document.storage;

import com.example.smartkb.common.BusinessException;
import com.example.smartkb.common.ErrorCode;
import com.example.smartkb.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * MinIO 文件存储操作类 —— 上传、下载、删除和 bucket 自动创建。
 * <p>
 * 对象路径格式：{@code {userId}/{kbId}/{uuid}.{ext}}。
 * 提供两种删除方式：
 * <ul>
 *   <li>{@code remove()}：严格模式，失败抛异常，用于正常删除链路</li>
 *   <li>{@code removeQuietly()}：静默模式，失败仅记录日志，用于上传后的补偿清理</li>
 * </ul>
 */
@Slf4j
@Component
public class MinioFileStorage {

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioFileStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    /** 上传文件到 MinIO，自动创建 bucket（若不存在） */
    public void upload(MultipartFile file, String objectName) {
        try {
            ensureBucketExists();
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .stream(inputStream, file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build());
            }
        } catch (Exception exception) {
            log.error("Failed to upload file to MinIO: objectName={}", objectName, exception);
            throw new BusinessException(ErrorCode.DOCUMENT_UPLOAD_FAILED, "文件上传失败", exception);
        }
    }

    /**
     * 静默删除 MinIO 对象 —— 用于上传后补偿。
     * 失败仅记录警告，不向上抛异常（补偿不应影响主流程）。
     */
    public void removeQuietly(String objectName) {
        try {
            remove(objectName);
        } catch (BusinessException exception) {
            log.warn("Failed to compensate MinIO object: objectName={}", objectName, exception);
        }
    }

    /**
     * 严格删除 MinIO 对象 —— 用于正常删除链路。
     * 失败抛异常，确保调用方获知并可重试。
     */
    public void remove(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception exception) {
            log.error("Failed to remove file from MinIO: objectName={}", objectName, exception);
            throw new BusinessException(ErrorCode.DOCUMENT_FILE_DELETE_FAILED, "删除文档文件失败", exception);
        }
    }

    /** 从 MinIO 下载文件流（调用方负责关闭 InputStream） */
    public InputStream download(String objectName) {
        try {
            GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectName)
                    .build());
            return response;
        } catch (Exception exception) {
            log.error("Failed to download file from MinIO: objectName={}", objectName, exception);
            throw new BusinessException(ErrorCode.DOCUMENT_READ_FAILED, "读取文档文件失败", exception);
        }
    }

    /** 确保 documents bucket 已存在，不存在则自动创建 */
    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(properties.getBucket())
                    .build());
        }
    }
}
