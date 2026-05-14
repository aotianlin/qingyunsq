package com.campusforum.infra;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioStorageService(@Value("${storage.minio.endpoint}") String endpoint,
                                @Value("${storage.minio.access-key}") String accessKey,
                                @Value("${storage.minio.secret-key}") String secretKey,
                                @Value("${storage.minio.bucket:campusforum}") String bucket) {
        this.bucket = bucket;
        this.client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
        log.info("MinioStorageService initialized, bucket={}", bucket);
    }

    private void ensureBucket() {
        try {
            boolean found = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure bucket: {}", e.getMessage());
        }
    }

    @Override
    public String upload(InputStream inputStream, String originalName, String contentType) {
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0) ext = originalName.substring(dot);

        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = UUID.randomUUID() + ext;
        String storageKey = dateDir + "/" + filename;

        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(inputStream, inputStream.available(), -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());
            log.info("File uploaded to MinIO: {}", storageKey);
            return storageKey;
        } catch (Exception e) {
            log.error("MinIO upload failed", e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public InputStream download(String storageKey) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
        } catch (Exception e) {
            log.warn("MinIO download failed: {}", storageKey);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
            log.info("File deleted from MinIO: {}", storageKey);
        } catch (Exception e) {
            log.warn("MinIO delete failed: {}", storageKey, e);
        }
    }
}
