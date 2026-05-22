package com.campusforum.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料更新请求 DTO。
 *
 * <p>安全加固（缺陷 1.20）：</p>
 * <ul>
 *   <li>{@code avatarUrl}/{@code profileCoverUrl} 强制 http(s) 协议，防止 {@code javascript:}
 *       / {@code data:} URL 触发 XSS / Open Redirect；</li>
 *   <li>{@code bio} 长度从 255 缩为 200 字符，避免超长内容造成存储与渲染压力；</li>
 *   <li>service 层会进一步对 URL 做域名白名单校验（{@code security.upload.allowed-asset-hosts}）。</li>
 * </ul>
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 64, message = "昵称最长 64 位")
    private String nickname;

    @Size(max = 500, message = "头像 URL 最长 500 位")
    @Pattern(regexp = "^$|^https?://.+", message = "头像 URL 必须以 http:// 或 https:// 开头")
    private String avatarUrl;

    @Size(max = 500, message = "个人主页封面 URL 最长 500 位")
    @Pattern(regexp = "^$|^https?://.+", message = "封面 URL 必须以 http:// 或 https:// 开头")
    private String profileCoverUrl;

    @Size(max = 200, message = "个人简介最长 200 字符")
    private String bio;

    @Size(max = 64, message = "学院最长 64 位")
    private String college;

    @Size(max = 64, message = "专业最长 64 位")
    private String major;

    @Size(max = 8, message = "年级最长 8 位")
    private String grade;
}
