package com.campusforum.security.upload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.infra.StorageService;
import com.campusforum.infra.security.MimeTypeValidator;
import com.campusforum.resource.domain.Resource;
import com.campusforum.resource.dto.ResourceVO;
import com.campusforum.resource.dto.UploadResourceRequest;
import com.campusforum.resource.mapper.ResourceMapper;
import com.campusforum.resource.service.ResourceService;
import com.campusforum.space.mapper.SpaceMapper;
import com.campusforum.space.mapper.SpaceMemberMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单元测试：{@link ResourceService#upload} 的存储调用必须显式传入 {@code file.getSize()}。
 *
 * <p>对应任务 T4.4，关联 bugfix.md 漏洞 6（MinIO available 截断）。</p>
 *
 * <p>核心断言：</p>
 * <ol>
 *   <li>{@link StorageService#upload(InputStream, String, String, long)} 4 参版本被调用 1 次；</li>
 *   <li>第 4 个参数等于 {@link org.springframework.web.multipart.MultipartFile#getSize()}，
 *       而不是 {@code -1}（表示走 default 兼容路径）或 {@code InputStream.available()}
 *       的 buffer 大小估算值。</li>
 * </ol>
 *
 * <p>测试策略：不启动 Spring 容器，所有依赖通过 Mockito 构造，
 * 用反射调用包级构造器以注入 {@code allowedExtensionsConfig}。
 * 该测试与现有依赖虚拟机数据库的 {@code ResourceServiceTest} 完全独立，
 * 可在 CI 中以纯单元测试形式跑通。</p>
 */
class ResourceServiceUploadSizeTest {

    /** 被测对象。 */
    private ResourceService resourceService;

    /** Mock 协作者：仅 storageService 与 mimeTypeValidator 被本测试关注。 */
    private StorageService storageService;
    private MimeTypeValidator mimeTypeValidator;
    private ResourceMapper resourceMapper;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() {
        resourceMapper = mock(ResourceMapper.class);
        userMapper = mock(UserMapper.class);
        SpaceMapper spaceMapper = mock(SpaceMapper.class);
        SpaceMemberMapper spaceMemberMapper = mock(SpaceMemberMapper.class);
        storageService = mock(StorageService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        mimeTypeValidator = mock(MimeTypeValidator.class);

        // 默认扩展名白名单，覆盖测试需要的 pdf 类型
        String allowedExtensions = "pdf,doc,docx,ppt,pptx,xls,xlsx,jpg,jpeg,png,gif,webp,md,markdown";

        resourceService = new ResourceService(
                resourceMapper, userMapper, spaceMapper, spaceMemberMapper,
                storageService, objectMapper, mimeTypeValidator, allowedExtensions);
    }

    @Test
    @DisplayName("ResourceService.upload：必须把 file.getSize() 显式传入 StorageService.upload 4 参版本")
    void upload_passesFileSizeToStorageService() {
        // 准备：构造 1024 字节的 PDF 内容
        byte[] payload = new byte[1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.pdf", "application/pdf", payload);
        long expectedSize = file.getSize();
        assertThat(expectedSize).isEqualTo(1024L);

        // mimeTypeValidator 不抛异常 → 进入 SHA-256 + 上传分支
        doNothing().when(mimeTypeValidator).validate(any(), anyString());

        // storageService.upload 返回 storageKey
        String expectedKey = "2025-11-25/uuid.pdf";
        when(storageService.upload(any(InputStream.class), anyString(), anyString(), anyLong()))
                .thenReturn(expectedKey);

        // resourceMapper.selectOne(...)：模拟"无重复 SHA-256"，正常落库
        when(resourceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(resourceMapper.insert(any(Resource.class))).thenReturn(1);

        // userMapper.selectById：让 toVO 流程能拿到一个用户对象
        User uploader = new User();
        uploader.setId(99L);
        uploader.setNickname("tester");
        when(userMapper.selectById(99L)).thenReturn(uploader);

        // 执行
        UploadResourceRequest req = new UploadResourceRequest();
        ResourceVO vo = resourceService.upload(99L, file, req);

        // 断言 1：返回 VO 非空且字段正确
        assertThat(vo).isNotNull();
        assertThat(vo.getFileName()).isEqualTo("lecture.pdf");
        assertThat(vo.getFileSize()).isEqualTo(expectedSize);

        // 断言 2：StorageService.upload 4 参版本被调用一次
        ArgumentCaptor<Long> sizeCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ctCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService, times(1)).upload(
                any(InputStream.class), nameCaptor.capture(), ctCaptor.capture(), sizeCaptor.capture());

        // 关键断言：size 参数必须等于 file.getSize()，不是 -1（旧 default 路径），
        // 也不是 InputStream.available() 的 buffer 估算值。
        assertThat(sizeCaptor.getValue())
                .as("漏洞 6 修复：必须显式传入 MultipartFile.getSize()，禁止 -1 或 available() 估算")
                .isEqualTo(expectedSize);

        assertThat(nameCaptor.getValue()).isEqualTo("lecture.pdf");
        assertThat(ctCaptor.getValue()).isEqualTo("application/pdf");

        // 断言 3：MimeTypeValidator 在上传前被调用，扩展名为 "pdf"
        verify(mimeTypeValidator, times(1)).validate(eq(file), eq("pdf"));
    }
}
