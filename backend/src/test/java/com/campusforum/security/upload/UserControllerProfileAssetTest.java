package com.campusforum.security.upload;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.infra.StorageService;
import com.campusforum.infra.security.MimeTypeValidator;
import com.campusforum.post.service.FavoriteService;
import com.campusforum.user.controller.UserController;
import com.campusforum.user.dto.UserAssetUploadVO;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试：{@link UserController#uploadProfileAsset(org.springframework.web.multipart.MultipartFile)}。
 *
 * <p>对应任务 T4.4，关联 bugfix.md 漏洞 6（MinIO available 截断）与漏洞 15
 * （profile asset URL 错误）。</p>
 *
 * <p>测试目标：</p>
 * <ol>
 *   <li>调用 {@link StorageService#upload(InputStream, String, String, long)} 时
 *       <b>显式传入 {@code MultipartFile.getSize()}</b>，禁止再走旧 3 参 default
 *       兼容路径（漏洞 6）；</li>
 *   <li>响应中的 {@code url} 字段来自 {@link StorageService#issuePublicGetUrl(String)}，
 *       而不再按 {@code storage.type=local} 拼接 {@code /uploads/<key>} 或直接回显
 *       {@code storageKey} 字面量（漏洞 15）；</li>
 *   <li>{@link MimeTypeValidator#validate} 在主流程被调用，确保 Tika 真实 MIME 校验
 *       未被本次重构绕过。</li>
 * </ol>
 *
 * <p>测试策略：不启动 Spring 容器，全部用 Mockito 构造 {@link UserController}，
 * 用 {@code mockStatic} 拦截 {@link StpUtil#checkLogin()} 静态调用，
 * 用 {@link MockMultipartFile} 提供真实的 size / originalFilename / contentType / inputStream。</p>
 */
class UserControllerProfileAssetTest {

    /** 被测对象。 */
    private UserController controller;

    /** Mock 协作者：本测试关注 storageService 与 mimeTypeValidator。 */
    private StorageService storageService;
    private MimeTypeValidator mimeTypeValidator;

    /** Sa-Token 静态 mock 句柄，{@link AfterEach} 中关闭。 */
    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        // userService / favoriteService 在本接口路径上不会被触达，给空 mock 即可
        UserService userService = mock(UserService.class);
        FavoriteService favoriteService = mock(FavoriteService.class);
        storageService = mock(StorageService.class);
        mimeTypeValidator = mock(MimeTypeValidator.class);

        controller = new UserController(userService, storageService, favoriteService, mimeTypeValidator);

        // 拦截 StpUtil.checkLogin() 静态方法，避免触发真实 Sa-Token 上下文
        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::checkLogin).then(invocation -> null);
    }

    @AfterEach
    void tearDown() {
        if (stpUtilMock != null) {
            stpUtilMock.close();
        }
    }

    @Test
    @DisplayName("uploadProfileAsset：必须把 MultipartFile.getSize() 显式传给 StorageService.upload，并以 issuePublicGetUrl 返回 URL")
    void uploadProfileAsset_passesFileSize_andReturnsPublicUrl() throws IOException {
        // 准备：构造一份 200 字节的 PNG MultipartFile，覆盖 5MB 限制下的常见场景
        byte[] payload = new byte[200];
        MockMultipartFile file = new MockMultipartFile(
                "file",                  // form 字段名
                "avatar.png",            // originalFilename
                "image/png",             // contentType
                payload                  // 内容字节
        );
        long expectedSize = file.getSize();
        assertThat(expectedSize)
                .as("前置条件：MockMultipartFile.getSize() 应等于实际字节数 200")
                .isEqualTo(200L);

        // mimeTypeValidator.validate 不抛异常 → 走到上传分支
        doNothing().when(mimeTypeValidator).validate(any(), anyString());

        // storageService.upload 返回固定 storageKey
        String expectedStorageKey = "2025-11-25/abcdef-avatar.png";
        when(storageService.upload(any(InputStream.class), anyString(), anyString(), anyLong()))
                .thenReturn(expectedStorageKey);

        // storageService.issuePublicGetUrl 返回固定 presigned URL
        String expectedUrl = "https://minio.example.com/campusforum/2025-11-25/abcdef-avatar.png?X-Amz-Sig=xxx";
        when(storageService.issuePublicGetUrl(expectedStorageKey)).thenReturn(expectedUrl);

        // 执行
        R<UserAssetUploadVO> response = controller.uploadProfileAsset(file);

        // 断言 1：响应体非空且包含正确的 url / storageKey
        assertThat(response).isNotNull();
        assertThat(response.getData()).isNotNull();
        assertThat(response.getData().getStorageKey())
                .as("storageKey 必须原样回显由 StorageService 颁发的 key")
                .isEqualTo(expectedStorageKey);
        assertThat(response.getData().getUrl())
                .as("url 必须来自 StorageService.issuePublicGetUrl，而不是 /uploads/<key> 拼接（漏洞 15）")
                .isEqualTo(expectedUrl);

        // 断言 2：StorageService.upload 4 参版本被调用 1 次，且 size 等于 file.getSize()
        ArgumentCaptor<Long> sizeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ctCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService, times(1)).upload(
                any(InputStream.class), nameCaptor.capture(), ctCaptor.capture(), sizeCaptor.capture());

        assertThat(sizeCaptor.getValue())
                .as("漏洞 6 修复：必须显式传入 MultipartFile.getSize()，禁止用 InputStream.available()")
                .isEqualTo(expectedSize);
        assertThat(nameCaptor.getValue()).isEqualTo("avatar.png");
        assertThat(ctCaptor.getValue()).isEqualTo("image/png");

        // 断言 3：issuePublicGetUrl 被调用一次且参数为上面的 storageKey
        verify(storageService, times(1)).issuePublicGetUrl(eq(expectedStorageKey));
    }

    @Test
    @DisplayName("uploadProfileAsset：在调用 StorageService 前必须先执行 MimeTypeValidator.validate（防止扩展名伪造绕过）")
    void uploadProfileAsset_callsValidateProfileImage_beforeUpload() throws IOException {
        // 准备：1 KB 的 jpg 文件
        MockMultipartFile file = new MockMultipartFile(
                "file", "selfie.jpg", "image/jpeg", new byte[1024]);

        when(storageService.upload(any(InputStream.class), anyString(), anyString(), anyLong()))
                .thenReturn("2025/x.jpg");
        when(storageService.issuePublicGetUrl(anyString()))
                .thenReturn("https://minio.example.com/x.jpg?sig=1");

        // 执行
        controller.uploadProfileAsset(file);

        // 断言：mimeTypeValidator.validate 被调用一次，扩展名为 "jpg"（非 jpeg，
        // 因为 controller 内部对 .jpg 后缀映射为 ext = "jpg"）。
        verify(mimeTypeValidator, times(1)).validate(eq(file), eq("jpg"));

        // 断言：storageService.upload 之后才能被触达 — 这里用调用次数兜底
        verify(storageService, times(1))
                .upload(any(InputStream.class), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("uploadProfileAsset：MimeTypeValidator 抛异常时主流程必须中断，绝不能继续调用 StorageService.upload")
    void uploadProfileAsset_validatorThrows_doesNotCallUpload() throws IOException {
        // 准备：合法的 png 文件
        MockMultipartFile file = new MockMultipartFile(
                "file", "trojan.png", "image/png", new byte[16]);

        // 模拟 Tika 检测出真实 MIME 与扩展名不一致
        org.mockito.Mockito.doThrow(new com.campusforum.infra.security.MimeMismatchException("扩展名与真实类型不一致"))
                .when(mimeTypeValidator).validate(any(), anyString());

        // 执行 + 断言：异常向上传播
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> controller.uploadProfileAsset(file))
                .isInstanceOf(com.campusforum.infra.security.MimeMismatchException.class);

        // 关键断言：StorageService.upload 必须 0 次调用，避免恶意文件被持久化
        verify(storageService, never())
                .upload(any(InputStream.class), anyString(), anyString(), anyLong());
        verify(storageService, never()).issuePublicGetUrl(anyString());
    }
}
