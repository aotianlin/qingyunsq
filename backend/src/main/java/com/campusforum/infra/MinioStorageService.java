package com.campusforum.infra;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO / S3 兼容对象存储实现。
 *
 * <p>对应 bugfix.md 漏洞 6：早期 {@code upload} 实现使用 {@code inputStream.available()}
 * 作为对象大小传给 MinIO SDK，导致 ≥ ~64KB 文件被截断到 buffer 大小。T4.2 修复要点：</p>
 * <ul>
 *   <li>仅暴露 4 参 {@code upload}，要求调用方显式传入 {@code size}；</li>
 *   <li>{@code putObject} 完成后立即 {@code statObject} 回查，size 不一致即
 *       {@code removeObject} 回滚并抛 {@link BusinessException}；</li>
 *   <li>新增 {@code issuePublicGetUrl} 颁发 presigned GET，TTL 取 5 分钟（漏洞 15）。</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "minio")
public class MinioStorageService implements StorageService {

    private final MinioClient client;
    private final String bucket;

    @Autowired
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

    /**
     * 包级可见构造器，便于单元测试注入 mock {@link MinioClient}，
     * 跳过真实的 endpoint / 桶探测逻辑。生产代码请勿调用此构造器。
     *
     * <p>必须在生产构造器上加 {@link Autowired @Autowired}：本类同时存在两个
     * 构造器（public 4 参 + package-private 2 参）后，Spring 默认无法选择哪个
     * 用于自动装配，会回退到查找无参构造器并抛
     * {@code NoSuchMethodException: <init>()}（参考 task T4.2 修复后的回归报错）。
     * 显式注解后只有 4 参版本参与自动装配，2 参版本仅供测试代码 new 使用。</p>
     */
    MinioStorageService(MinioClient client, String bucket) {
        this.client = client;
        this.bucket = bucket;
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

    /** 提取原始文件名扩展名（含点号），无扩展名时返回空串。 */
    private String extractExt(String originalName) {
        if (originalName == null) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 ? originalName.substring(dot) : "";
    }

    /** 用 {@code yyyy-MM-dd/<uuid><ext>} 形式构造对象 key，避免单目录文件过多。 */
    private String buildStorageKey(String ext) {
        String dateDir = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String filename = UUID.randomUUID() + ext;
        return dateDir + "/" + filename;
    }

    @Override
    public String upload(InputStream inputStream, String originalName, String contentType, long size) {
        // 漏洞 6 修复要点 1：MinIO 实现强制拒绝未知 size 上传，避免 SDK 内部用 available() 估算
        if (size < 0) {
            throw new IllegalArgumentException(
                    "MinioStorageService 必须显式传入 size，禁止使用 inputStream.available() 估算");
        }

        String storageKey = buildStorageKey(extractExt(originalName));
        try {
            // 漏洞 6 修复要点 2：用显式 size + partSize=-1，让 SDK 走单段 put 走精确字节数
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .stream(inputStream, size, -1)
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build());

            // 漏洞 6 修复要点 3：上传完成后立即回查对象 size，不一致即回滚
            // 这样能捕获 SDK 在 retry / 网络抖动等异常路径下出现的写入截断
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .build());
            if (stat.size() != size) {
                log.error("MinIO 上传字节数不匹配 expected={}, actual={}, key={}",
                        size, stat.size(), storageKey);
                // 回滚：避免半截文件留在 bucket 里被去重 SHA 当作有效对象
                try {
                    client.removeObject(RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(storageKey)
                            .build());
                } catch (Exception removeEx) {
                    log.warn("回滚截断对象失败 key={}: {}", storageKey, removeEx.getMessage());
                }
                throw new BusinessException(ErrorCode.STORAGE_ERROR);
            }

            log.info("File uploaded to MinIO: {} ({} bytes)", storageKey, size);
            return storageKey;
        } catch (BusinessException e) {
            // 业务异常按原貌透传给上层 GlobalExceptionHandler
            throw e;
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

    @Override
    public String issuePublicGetUrl(String storageKey) {
        try {
            // 漏洞 15 修复：头像 / 封面等公开资源用 presigned GET 短期访问。
            // TTL 取 300s = 5 分钟，比下载场景的 60s 宽松一档，避开 CDN / 浏览器渲染缓存抖动。
            // 当前类未注入 SecurityProperties 以保持构造器轻量；如后续接入 5×ttlSeconds 配置，
            // 改为构造注入即可，无需调整调用方契约。
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(storageKey)
                    .method(Method.GET)
                    .expiry(300, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO presign failed: {}", storageKey, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }
}
