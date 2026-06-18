package com.campusforum.infra;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * 阿里云 OSS 存储实现。
 *
 * <p>对应 bugfix.md 漏洞 6 / 15 的接口扩展（T4.3）：</p>
 * <ul>
 *   <li>4 参 {@code upload}：当 {@code size ≥ 0} 时通过 {@link ObjectMetadata#setContentLength}
 *       显式告知 OSS SDK 总长度，避免内部 buffer 截断；{@code size = -1} 时退回到旧的
 *       "纯流式"分支（仍可工作，但不享受 size 校验保护）。</li>
 *   <li>新增 {@code issuePublicGetUrl}：调用 {@link OSS#generatePresignedUrl(String, String, Date)}
 *       颁发 5 分钟 TTL 的 GET URL，用于头像 / 封面公开访问。</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssStorageService implements StorageService {

    /** 公开访问 URL 默认 TTL（毫秒），5 分钟，与 MinIO 实现保持一致。 */
    private static final long PUBLIC_URL_TTL_MILLIS = 300L * 1000L;

    private final OSS client;
    private final String bucket;

    @Autowired
    public OssStorageService(@Value("${storage.oss.endpoint}") String endpoint,
                              @Value("${storage.oss.access-key}") String accessKey,
                              @Value("${storage.oss.secret-key}") String secretKey,
                              @Value("${storage.oss.bucket:campusforum}") String bucket) {
        this.bucket = bucket;
        this.client = new OSSClientBuilder().build(endpoint, accessKey, secretKey);
        ensureBucket();
        log.info("OssStorageService initialized, bucket={}", bucket);
    }

    /**
     * 包级可见构造器，便于单元测试注入 mock OSS client。生产代码请勿调用。
     */
    OssStorageService(OSS client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    private void ensureBucket() {
        try {
            if (!client.doesBucketExist(bucket)) {
                client.createBucket(bucket);
                log.info("Created bucket: {}", bucket);
            }
        } catch (Exception e) {
            log.warn("Failed to ensure bucket: {}", e.getMessage());
        }
    }

    @Override
    public String upload(InputStream inputStream, String originalName, String contentType, long size) {
        String ext = "";
        if (originalName != null) {
            int dot = originalName.lastIndexOf('.');
            if (dot >= 0) {
                ext = originalName.substring(dot);
            }
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = UUID.randomUUID() + ext;
        String storageKey = dateDir + "/" + filename;

        try {
            // 漏洞 6 修复要点：size ≥ 0 时显式 setContentLength，让 OSS SDK 走"已知长度"分支，
            // 避免内部按 8KB buffer 估算导致截断；size < 0 时保留原"纯流式"行为做兼容
            ObjectMetadata metadata = new ObjectMetadata();
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            if (size >= 0) {
                metadata.setContentLength(size);
            }

            PutObjectRequest request = new PutObjectRequest(bucket, storageKey, inputStream, metadata);
            client.putObject(request);

            log.info("File uploaded to OSS: {} ({} bytes)", storageKey, size);
            return storageKey;
        } catch (Exception e) {
            log.error("OSS upload failed", e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public InputStream download(String storageKey) {
        try {
            OSSObject object = client.getObject(bucket, storageKey);
            return object.getObjectContent();
        } catch (Exception e) {
            log.warn("OSS download failed: {}", storageKey);
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            client.deleteObject(bucket, storageKey);
            log.info("File deleted from OSS: {}", storageKey);
        } catch (Exception e) {
            log.warn("OSS delete failed: {}", storageKey, e);
        }
    }

    @Override
    public String issuePublicGetUrl(String storageKey) {
        try {
            // 漏洞 15 修复：用 OSS SDK 的 presigned GET，TTL = 5 分钟
            Date expiration = new Date(System.currentTimeMillis() + PUBLIC_URL_TTL_MILLIS);
            URL url = client.generatePresignedUrl(bucket, storageKey, expiration);
            return url.toString();
        } catch (Exception e) {
            log.error("OSS presign failed: {}", storageKey, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }
}
