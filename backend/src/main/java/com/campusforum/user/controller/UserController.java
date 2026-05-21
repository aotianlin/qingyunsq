package com.campusforum.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.infra.StorageService;
import com.campusforum.user.dto.UpdateProfileRequest;
import com.campusforum.user.dto.UserAssetUploadVO;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StorageService storageService;

    @Value("${storage.type:local}")
    private String storageType;

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

    @PostMapping("/me/assets")
    public R<UserAssetUploadVO> uploadProfileAsset(@RequestParam("file") MultipartFile file) throws IOException {
        StpUtil.checkLogin();
        validateProfileImage(file);
        String storageKey = storageService.upload(file.getInputStream(), file.getOriginalFilename(), file.getContentType());
        String url = "local".equalsIgnoreCase(storageType) ? "/uploads/" + storageKey : storageKey;
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
    public R<UserVO> getById(@PathVariable Long id) {
        return R.ok(userService.getById(id));
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
        if (!(lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".webp"))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "仅支持 JPG、PNG、GIF、WEBP 图片");
        }
    }
}
