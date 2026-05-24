package com.campusforum.security.upload;

import com.campusforum.infra.StorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StorageService} 接口契约测试。
 *
 * <p>对应 T4.1（bugfix.md 漏洞 6 / 15）：接口扩展为 4 参 {@code upload} +
 * {@code issuePublicGetUrl}，旧 3 参 {@code upload} 标记 {@code @Deprecated} 但保留
 * default 实现作为兼容期 shim。本测试用一个本地 fake 实现验证两点：</p>
 * <ol>
 *   <li>调用旧 3 参签名时，default 方法必须以 {@code size = -1L} 透传到 4 参版本，
 *       这是 MinIO 实现拒绝旧路径的依据；</li>
 *   <li>调用 4 参签名时，参数被原样传递、不会被 default 方法改写。</li>
 * </ol>
 *
 * <p>本测试不依赖 Spring 上下文，直接用匿名类实现接口，避免引入 mock 框架。</p>
 */
class StorageServiceContractTest {

    /**
     * 一个仅记录最后一次 4 参 upload 入参的 fake 实现，用于观察 default 方法的转发行为。
     */
    private static final class RecordingStorage implements StorageService {

        final AtomicReference<String> lastOriginalName = new AtomicReference<>();
        final AtomicReference<String> lastContentType = new AtomicReference<>();
        final AtomicLong lastSize = new AtomicLong(Long.MIN_VALUE);
        final AtomicLong invokeCount = new AtomicLong();

        @Override
        public String upload(InputStream inputStream, String originalName,
                             String contentType, long size) {
            lastOriginalName.set(originalName);
            lastContentType.set(contentType);
            lastSize.set(size);
            invokeCount.incrementAndGet();
            return "fake/" + originalName;
        }

        @Override
        public InputStream download(String storageKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String storageKey) {
            // 测试场景下不做任何操作
        }

        @Override
        public String issuePublicGetUrl(String storageKey) {
            return "fake-url://" + storageKey;
        }
    }

    @Test
    void legacy3argUpload_forwardsTo4argWithMinusOne() {
        // 准备：fake 实现
        RecordingStorage storage = new RecordingStorage();

        // 执行：调用旧 3 参签名（已 @Deprecated，但兼容期内 default 方法仍可工作）
        @SuppressWarnings("deprecation")
        String result = storage.upload(
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                "legacy.png",
                "image/png");

        // 断言 1：default 方法以 size = -1L 透传给 4 参版本
        assertThat(storage.lastSize.get())
                .as("旧 3 参签名必须以 size = -1L 透传，作为 MinIO 拒绝旧路径的依据")
                .isEqualTo(-1L);

        // 断言 2：其余参数原样透传
        assertThat(storage.lastOriginalName.get()).isEqualTo("legacy.png");
        assertThat(storage.lastContentType.get()).isEqualTo("image/png");

        // 断言 3：4 参版本被调用且仅一次
        assertThat(storage.invokeCount.get()).isEqualTo(1L);

        // 断言 4：返回值原样向上透出
        assertThat(result).isEqualTo("fake/legacy.png");
    }

    @Test
    void newUpload_passesSizeUnchanged() {
        // 准备
        RecordingStorage storage = new RecordingStorage();

        // 执行：直接调用 4 参版本，传入显式 size
        String result = storage.upload(
                new ByteArrayInputStream(new byte[]{1, 2, 3, 4, 5}),
                "real.pdf",
                "application/pdf",
                5L);

        // 断言：4 参签名下 size 必须保持调用方传入的值，不会被 default 方法改写
        assertThat(storage.lastSize.get()).isEqualTo(5L);
        assertThat(storage.lastOriginalName.get()).isEqualTo("real.pdf");
        assertThat(storage.lastContentType.get()).isEqualTo("application/pdf");
        assertThat(storage.invokeCount.get()).isEqualTo(1L);
        assertThat(result).isEqualTo("fake/real.pdf");
    }
}
