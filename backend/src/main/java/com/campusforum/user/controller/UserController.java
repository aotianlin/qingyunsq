package com.campusforum.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.infra.StorageService;
import com.campusforum.post.dto.FavoriteVO;
import com.campusforum.post.service.FavoriteService;
import com.campusforum.user.dto.UpdateProfileRequest;
import com.campusforum.user.dto.UserAssetUploadVO;
import com.campusforum.user.dto.PublicUserVO;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StorageService storageService;
    private final FavoriteService favoriteService;
    private final com.campusforum.infra.security.MimeTypeValidator mimeTypeValidator;

    @GetMapping("/me")
    public R<UserVO> getMe() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getById(userId));
    }

    @PutMapping("/me")
    public R<UserVO> updateMe(@Valid @RequestBody UpdateProfileRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.updateProfile(userId, req));
    }

    /**
     * 上传当前登录用户的头像 / 封面等公开访问素材，并返回公开访问 URL。
     *
     * <p>本方法在 T4.4 阶段重写，关联以下安全修复：</p>
     * <ul>
     *   <li><b>漏洞 6（MinIO available 截断）</b>：必须把 {@link MultipartFile#getSize()}
     *       作为显式 size 传给 4 参版本的 {@link StorageService#upload}，禁止再走旧 3 参 default
     *       兼容路径（后者以 {@code -1} 透传，MinIO 实现会直接拒绝）。
     *       这样 MinIO SDK 可使用 {@code stream(in, size, -1)} 精确读取并在上传完成后用
     *       {@code statObject} 回查 size，杜绝因 {@code InputStream#available()} 误读 buffer
     *       大小（约 8KB）导致的"大文件被截断"问题。</li>
     *   <li><b>漏洞 15（profile asset URL 错误）</b>：早期实现按 {@code storage.type=local}
     *       拼接 {@code /uploads/<key>}，在 minio / oss 模式下直接返回 storageKey 字面量，
     *       前端无法访问。统一改为调用 {@link StorageService#issuePublicGetUrl(String)}
     *       由各实现自行颁发可用的访问 URL（MinIO/OSS 走 presigned，Local 走站内代理签名）。</li>
     * </ul>
     */
    @PostMapping("/me/assets")
    public R<UserAssetUploadVO> uploadProfileAsset(@RequestParam("file") MultipartFile file) throws IOException {
        StpUtil.checkLogin();
        validateProfileImage(file);
        // 漏洞 6 修复：必须传 file.getSize() 而非 inputStream.available()
        String storageKey = storageService.upload(
                file.getInputStream(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize());
        // 漏洞 15 修复：统一通过 StorageService 颁发公开访问 URL，
        // 不再按 storage.type 走分支拼接 /uploads/ 路径或直接回显 storageKey
        String url = storageService.issuePublicGetUrl(storageKey);
        return R.ok(UserAssetUploadVO.builder()
                .url(url)
                .storageKey(storageKey)
                .build());
    }

    @GetMapping("/me/mute-settings")
    public R<Set<String>> getMuteSettings() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getMuteSettings(userId));
    }

    @PutMapping("/me/mute-settings")
    public R<?> updateMuteSettings(@RequestBody Map<String, Set<String>> body) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.updateMuteSettings(userId, body.getOrDefault("mutedTypes", Set.of()));
        return R.ok();
    }

    @GetMapping("/me/tag-subscriptions")
    public R<Set<String>> getTagSubscriptions() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(userService.getTagSubscriptions(userId));
    }

    @PutMapping("/me/tag-subscriptions")
    public R<?> updateTagSubscriptions(@RequestBody Map<String, Set<String>> body) {
        long userId = StpUtil.getLoginIdAsLong();
        userService.updateTagSubscriptions(userId, body.getOrDefault("tags", Set.of()));
        return R.ok();
    }

    @GetMapping("/{id}")
    public R<PublicUserVO> getById(@PathVariable Long id) {
        // 安全加固：查看他人资料只返回最小披露的 PublicUserVO，
        // 避免遍历 id 收集同租户用户的 email / studentNo 等 PII。
        // 查看本人完整资料请走 GET /api/v1/users/me。
        return R.ok(userService.getPublicById(id));
    }

    @GetMapping("/me/favorites")
    public R<List<FavoriteVO>> getFavorites(
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(favoriteService.listFavorites(userId, targetType, cursor, limit));
    }

    private void validateProfileImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "请选择要上传的图片");
        }
        if (file.getSize() > 5 * 1024 * 1024L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "图片不能超过 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "只能上传图片文件");
        }
        String originalName = file.getOriginalFilename();
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        String ext = null;
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) ext = lowerName.endsWith(".jpeg") ? "jpeg" : "jpg";
        else if (lowerName.endsWith(".png")) ext = "png";
        else if (lowerName.endsWith(".gif")) ext = "gif";
        else if (lowerName.endsWith(".webp")) ext = "webp";
        if (ext == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "仅支持 JPG、PNG、GIF、WEBP 图片");
        }
        // 安全加固（缺陷 1.22）：用 Tika 检测真实 MIME，防止扩展名伪造
        mimeTypeValidator.validate(file, ext);
    }
}
