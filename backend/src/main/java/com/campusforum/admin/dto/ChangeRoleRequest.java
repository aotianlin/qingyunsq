package com.campusforum.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改用户角色请求 DTO（任务 T8.7 / 漏洞 17）。
 *
 * <p>替代原 {@code Map<String, String>} 接收方式，强制 Bean Validation：
 * 缺字段 / 非法 role 值在 controller 层就被 {@code MethodArgumentNotValidException}
 * 拦截，无需进入 service 才报错。</p>
 */
@Data
public class ChangeRoleRequest {

    /**
     * 目标角色。
     * <p>仅允许 {@code USER} / {@code TENANT_ADMIN}。
     * 提升为 {@code SUPER_ADMIN} 不在 admin API 范围内（防止权限提权）。</p>
     */
    @NotBlank(message = "role 不能为空")
    @Pattern(regexp = "^(USER|TENANT_ADMIN)$",
            message = "无效角色：仅允许 USER / TENANT_ADMIN")
    private String role;
}
