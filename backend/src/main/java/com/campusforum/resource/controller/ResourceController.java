package com.campusforum.resource.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
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

@RestController
@RequestMapping("/api/v1/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

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

    @GetMapping("/{id}/download")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        String fileName = resourceService.getFileName(id);
        InputStream is = resourceService.download(id);

        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);

        try (OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
        } catch (Exception e) {
            throw new RuntimeException("Download failed", e);
        }
    }

    @GetMapping("/{id}/preview")
    public void preview(@PathVariable Long id, HttpServletResponse response) {
        String fileName = resourceService.getFileName(id);
        String fileType = resourceService.getFileType(id);
        InputStream is = resourceService.preview(id);

        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(previewContentType(fileType));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded);
        response.setHeader("X-Content-Type-Options", "nosniff");

        try (OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
        } catch (Exception e) {
            throw new RuntimeException("Preview failed", e);
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

    private static String previewContentType(String fileType) {
        return switch (fileType == null ? "" : fileType.toLowerCase()) {
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG_VALUE;
            case "png" -> MediaType.IMAGE_PNG_VALUE;
            case "gif" -> MediaType.IMAGE_GIF_VALUE;
            case "webp" -> "image/webp";
            case "md", "markdown" -> "text/markdown; charset=UTF-8";
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }
}
