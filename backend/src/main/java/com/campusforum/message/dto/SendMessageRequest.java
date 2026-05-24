package com.campusforum.message.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送私信请求 DTO（任务 T8.7 / 漏洞 17）。
 *
 * <p>替代原 {@code Map<String, String>} 接收方式，强制类型 + 字段约束：</p>
 * <ul>
 *   <li>{@code receiverId}：必填，类型 {@code Long}（不再 String 手工 parseLong）；</li>
 *   <li>{@code content}：长度 ≤ 2000，避免巨型私信打爆 Redis / 邮件队列；</li>
 *   <li>{@code imageUrl}：可选；非空时必须是合法的 http/https URL 形式，
 *       长度 ≤ 500，配合 {@code SecurityProperties.upload.allowedAssetHosts /
 *       selfHosts} 白名单进一步收口（白名单校验在 MessageService 内做）。</li>
 * </ul>
 */
@Data
public class SendMessageRequest {

    @NotNull(message = "receiverId 不能为空")
    private Long receiverId;

    @Size(max = 2000, message = "私信内容长度不能超过 2000 字符")
    private String content;

    @Pattern(regexp = "^$|^https?://.+",
            message = "imageUrl 必须是 http / https 协议的 URL")
    @Size(max = 500, message = "imageUrl 长度不能超过 500 字符")
    private String imageUrl;
}
