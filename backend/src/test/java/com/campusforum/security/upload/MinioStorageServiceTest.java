package com.campusforum.security.upload;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.MinioStorageService;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MinioStorageService} 单元测试。
 *
 * <p>对应 T4.2（bugfix.md 漏洞 6 / 15）：</p>
 * <ul>
 *   <li>{@code upload(in, name, ct, size)} 必须显式接受 size，{@code size < 0} 直接拒绝；</li>
 *   <li>{@code putObject} 完成后必须 {@code statObject} 回查 size，不一致即
 *       {@code removeObject} 回滚并抛 {@link BusinessException}；</li>
 *   <li>{@code issuePublicGetUrl} 必须返回 SDK presigned URL。</li>
 * </ul>
 *
 * <p>本测试通过包级构造器注入 mock {@link MinioClient}，跳过真实 endpoint 探测，
 * 完全在 JVM 内运行，不依赖虚拟机上的真实 MinIO。</p>
 */
class MinioStorageServiceTest {

    private MinioClient client;
    private MinioStorageService service;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(MinioClient.class);
        // 包级可见构造器：MinioStorageService(MinioClient, String)
        // 用反射访问以绕过 package-private 限制（测试类在不同包下）
        Constructor<MinioStorageService> ctor = MinioStorageService.class
                .getDeclaredConstructor(MinioClient.class, String.class);
        ctor.setAccessible(true);
        service = ctor.newInstance(client, "campusforum");
    }

    @Test
    void upload_negativeSize_throwsIllegalArgument() {
        // 漏洞 6 修复要点：size < 0 直接拒绝，避免有人走旧的 available() 路径
        ByteArrayInputStream in = new ByteArrayInputStream("anything".getBytes());

        assertThatThrownBy(() -> service.upload(in, "x.png", "image/png", -1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须显式传入 size");
    }

    @Test
    void upload_size200_putsAndStatObject() throws Exception {
        // 准备：putObject 不抛异常；statObject 返回 size = 200 与传入一致
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(200L);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        // 执行
        byte[] payload = new byte[200];
        String key = service.upload(new ByteArrayInputStream(payload),
                "doc.pdf", "application/pdf", 200L);

        // 断言 1：返回的 storageKey 形如 "yyyy-MM-dd/<uuid>.pdf"
        assertThat(key)
                .as("storageKey 必须按 yyyy-MM-dd/<uuid>.pdf 形式生成")
                .matches("\\d{4}-\\d{2}-\\d{2}/[0-9a-fA-F-]+\\.pdf");

        // 断言 2：putObject 被调用 1 次
        verify(client, times(1)).putObject(any(PutObjectArgs.class));

        // 断言 3：statObject 被调用 1 次（用于 size 回查）
        verify(client, times(1)).statObject(any(StatObjectArgs.class));

        // 断言 4：成功路径不应触发 removeObject 回滚
        verify(client, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void upload_statObjectMismatch_rollsBackAndThrows() throws Exception {
        // 准备：putObject 不抛；statObject 返回 size = 8192（截断典型值），与声明的 200000 不一致
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(8192L);
        when(client.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        // 执行：必须抛 STORAGE_ERROR
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[10]);
        assertThatThrownBy(() -> service.upload(in, "big.bin",
                "application/octet-stream", 200000L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode() == ErrorCode.STORAGE_ERROR.getCode(),
                        "必须抛 STORAGE_ERROR 错误码");

        // 断言：检测到不一致后必须 removeObject 回滚，避免半截文件留在 bucket 里
        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(client, times(1)).removeObject(captor.capture());
        // RemoveObjectArgs 的 bucket 与 object 字段无 public getter，但 toString 至少包含 bucket 名
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void issuePublicGetUrl_returnsPresignedUrl() throws Exception {
        // 准备：mock SDK presigned 返回 URL
        String expectedUrl = "http://minio.local/campusforum/2025-11-25/avatar.png?X-Amz-Sig=abc";
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn(expectedUrl);

        // 执行
        String url = service.issuePublicGetUrl("2025-11-25/avatar.png");

        // 断言 1：原样返回 SDK 给出的 URL，不做拼接
        assertThat(url).isEqualTo(expectedUrl);

        // 断言 2：getPresignedObjectUrl 被调用一次
        verify(client, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    void issuePublicGetUrl_sdkException_throwsStorageError() throws Exception {
        // 准备：SDK 抛异常（模拟 endpoint 不可达 / 凭证错误）
        when(client.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenThrow(new RuntimeException("endpoint unreachable"));

        // 执行：必须包装为 BusinessException(STORAGE_ERROR)，不能直接向上抛 RuntimeException
        assertThatThrownBy(() -> service.issuePublicGetUrl("any-key"))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getCode() == ErrorCode.STORAGE_ERROR.getCode());
    }
}
