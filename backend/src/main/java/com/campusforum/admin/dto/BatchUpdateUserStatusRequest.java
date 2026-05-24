package com.campusforum.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量更新用户状态请求 DTO（任务 T8.7 / 漏洞 17）。
 *
 * <p>替代原 {@code Map<String, Object>} + 手工 cast 写法，把 "ids 非空 / 上限 100 /
 * status ∈ {0,1}" 等约束统一交给 Bean Validation。</p>
 */
@Data
public class BatchUpdateUserStatusRequest {

    /**
     * 目标用户 ID 列表。
     * <p>必须非空，单次调用最多 100 条（漏洞 16：避免 N+1 长事务被滥用）。</p>
     */
    @NotEmpty(message = "ids 不能为空")
    @Size(max = 100, message = "单次最多 100 条")
    private List<@NotNull Long> ids;

    /**
     * 目标状态：0 停用 / 1 启用。
     */
    @NotNull(message = "status 不能为空")
    @Min(value = 0, message = "status 取值范围 [0,1]")
    @Max(value = 1, message = "status 取值范围 [0,1]")
    private Integer status;
}
