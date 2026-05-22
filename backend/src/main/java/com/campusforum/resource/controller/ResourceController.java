package com.campusforum.resource.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.common.R;
import com.campusforum.infra.preview.PreviewProperties;
import com.campusforum.infra.security.SignedUrlService;
import com.campusforum.resource.dto.ResourcePreviewVO;
import com.campusforum.resource.dto.ResourceVO;
import com.campusforum.resource.dto.UploadResourceRequest;
import com.campusforum.resource.service.ResourceService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;
    private final PreviewProperties previewProperties;
    private final SignedUrlService signedUrlService;

    @PostMapping
    public R<ResourceVO> upload(
            @RequestParam("file") MultipartFile file,
            @ModelAttribute UploadResourceRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(resourceService.upload(userId, file, req));
    }

    @GetMapping
    public R<List<ResourceVO>> list(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String course,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(resourceService.list(spaceId, college, major, course, cursor, limit));
    }

    @GetMapping("/{id}")
    public R<ResourceVO> getById(@PathVariable Long id) {
        return R.ok(resourceService.getById(id));
    }

    /**
     * 申请短期下载签名。前端拿到 token 后拼到 download/preview URL 即可使用，
     * 真正的会话 token 不再随 URL 暴露到 access log/Referer/浏览器历史。
     */
    @GetMapping("/{id}/signed-url")
    public R<Map<String, Object>> signedUrl(@PathVariable Long id,
                                            @RequestParam(defaultValue = "download") String action) {
        long userId = StpUtil.getLoginIdAsLong();
        // 服务层会进行可见性校验，签名前先校验访问权限
        resourceService.getById(id);
        if (!"download".equals(action) && !"preview".equals(action)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "action 仅支持 download 或 preview");
        }
        SignedUrlService.SignedToken token = signedUrlService.sign(userId, "RESOURCE", id, action);
        return R.ok(Map.of(
                "token", token.token(),
                "expiresAt", token.expiresAtSeconds()
        ));
    }

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id,
                         @RequestParam(value = "sig", required = false) String signature,
                         HttpServletResponse response) {
        verifySignatureOrLogin(id, "download", signature);
        String fileName = resourceService.getFileName(id);
        InputStream is = resourceService.download(id);

        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);

        try (OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
        } catch (Exception e) {
            // 不向前端透出堆栈；下载流过程中失败通常是连接断开或 IO 错误
            throw new BusinessException(ErrorCode.STORAGE_ERROR.getCode(), "下载中断");
        }
    }

    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id,
                        @RequestParam(value = "sig", required = false) String signature,
                        HttpServletResponse response) {
        verifySignatureOrLogin(id, "preview", signature);
        ResourceVO resource = resourceService.getById(id);
        String fileType = resource.getFileType().toLowerCase();

        // 文件大小检查
        if (resource.getFileSize() > previewProperties.getMaxPreviewSize()) {
            response.setStatus(413);
            writeJson(response, "{\"code\":413,\"message\":\"文件过大，无法预览（最大 50MB）\"}");
            return;
        }

        // 判断文件类型
        String contentType = getPreviewContentType(fileType);
        if (contentType == null) {
            // Office 文档重定向到预览服务
            if (isOfficeFile(fileType)) {
                try {
                    String downloadUrl = "/api/v1/resources/" + id + "/download";
                    String previewUrl = previewProperties.getOfficeServiceUrl()
                            + "?url=" + URLEncoder.encode(downloadUrl, StandardCharsets.UTF_8);
                    response.sendRedirect(previewUrl);
                } catch (Exception e) {
                    response.setStatus(502);
                    writeJson(response, "{\"code\":502,\"message\":\"预览服务暂时不可用\"}");
                }
                return;
            }
            // 不支持的文件类型
            response.setStatus(415);
            writeJson(response, "{\"code\":415,\"message\":\"该文件类型不支持在线预览\"}");
            return;
        }

        // PDF 和图片：直接流式返回
        InputStream is = resourceService.preview(id);
        response.setContentType(contentType);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline");
        response.setHeader("X-Content-Type-Options", "nosniff");

        try (OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR.getCode(), "预览中断");
        }
    }

    @GetMapping("/{id}/preview-text")
    public R<ResourcePreviewVO> previewText(@PathVariable Long id) {
        return R.ok(resourceService.previewText(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        resourceService.delete(id, userId);
        return R.ok();
    }

    /**
     * 兼容老前端：若提供了 sig 参数，则用签名校验放行（适合 &lt;a href&gt;/&lt;img src&gt; 直链场景，
     * 此时浏览器不会附带 Authorization 头）；否则要求登录态 + 资源访问权限。
     */
    private void verifySignatureOrLogin(long resourceId, String action, String signature) {
        if (signature != null && !signature.isBlank()) {
            SignedUrlService.Verified verified = signedUrlService.verify(signature, "RESOURCE", resourceId, action);
            if (verified == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "下载链接已失效，请刷新页面重试");
            }
            return;
        }
        if (!StpUtil.isLogin()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        // 已登录路径仍然需要走 service 的可见性校验，由 download/preview 内部触发
    }

    private String getPreviewContentType(String fileType) {
        return switch (fileType) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "md", "markdown" -> "text/markdown; charset=UTF-8";
            default -> null;
        };
    }

    private boolean isOfficeFile(String fileType) {
        return switch (fileType) {
            case "doc", "docx", "ppt", "pptx", "xls", "xlsx" -> true;
            default -> false;
        };
    }

    private void writeJson(HttpServletResponse response, String json) {
        response.setContentType("application/json;charset=UTF-8");
        try {
            response.getWriter().write(json);
        } catch (Exception ignored) {}
    }
}
