package com.campusforum.infra.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 上传文件 MIME 类型校验器（缺陷 1.22 加固）。
 *
 * <p>原本仅按 {@code originalFilename} 后缀判断文件类型，攻击者可以将 PHP/HTML/EICAR
 * 等危险文件改名为 {@code .png} 上传绕过白名单。本组件用 Apache Tika 嗅探真实 MIME，
 * 与扩展名做交叉验证，不一致直接拒绝。</p>
 *
 * <p>设计权衡：</p>
 * <ul>
 *   <li>{@code .docx/.xlsx/.pptx} 实际是 ZIP 容器，Tika 检测出的 MIME 形如
 *       {@code application/x-tika-ooxml} / {@code application/zip}，因此扩展名 → MIME
 *       的映射使用集合表达 "可接受多种"；</li>
 *   <li>所有未在 {@link #EXT_TO_MIMES} 中显式声明的扩展名跳过 MIME 校验（避免误伤），
 *       保留原有的扩展名白名单 + 大小限制作为基础防线。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MimeTypeValidator {

    private final SecurityProperties securityProperties;

    private final Detector detector = TikaConfig.getDefaultConfig().getDetector();

    /** 扩展名 → 允许的 MIME 集合。空集合表示该扩展名跳过校验。 */
    private static final Map<String, Set<String>> EXT_TO_MIMES = Map.ofEntries(
            // 图片
            Map.entry("jpg",  Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("png",  Set.of("image/png")),
            Map.entry("gif",  Set.of("image/gif")),
            Map.entry("webp", Set.of("image/webp")),
            // PDF
            Map.entry("pdf",  Set.of("application/pdf")),
            // OOXML（实际是 ZIP）
            Map.entry("docx", Set.of(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/x-tika-ooxml",
                    "application/zip")),
            Map.entry("xlsx", Set.of(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/x-tika-ooxml",
                    "application/zip")),
            Map.entry("pptx", Set.of(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/x-tika-ooxml",
                    "application/zip")),
            // 压缩包
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")),
            // Markdown / 纯文本
            Map.entry("md",       Set.of("text/plain", "text/x-markdown", "text/markdown")),
            Map.entry("markdown", Set.of("text/plain", "text/x-markdown", "text/markdown"))
    );

    /**
     * 校验上传文件的真实 MIME 与扩展名是否一致。
     * 当 {@code security.upload.real-mime-check=false} 时跳过校验（开发联调便利）。
     */
    public void validate(MultipartFile file, String declaredExt) {
        if (!securityProperties.getUpload().isRealMimeCheck()) return;
        if (declaredExt == null || declaredExt.isBlank()) return;
        String ext = declaredExt.toLowerCase(Locale.ROOT);
        Set<String> allowed = EXT_TO_MIMES.get(ext);
        if (allowed == null) return; // 未声明的扩展名跳过

        String detected;
        try (InputStream raw = file.getInputStream();
             TikaInputStream tis = TikaInputStream.get(raw)) {
            Metadata meta = new Metadata();
            if (file.getOriginalFilename() != null) {
                meta.set(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
            }
            detected = detector.detect(tis, meta).toString().toLowerCase(Locale.ROOT);
        } catch (IOException e) {
            log.warn("MimeTypeValidator failed to read upload stream: {}", e.getMessage());
            throw new MimeMismatchException("无法识别文件类型");
        }

        if (!allowed.contains(detected)) {
            log.warn("MIME mismatch: ext=.{}, detected={}", ext, detected);
            throw new MimeMismatchException(
                    "文件扩展名 ." + ext + " 与实际类型 " + detected + " 不一致");
        }
    }
}
