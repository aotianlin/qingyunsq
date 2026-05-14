package com.campusforum.tenant.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tenants")
public class Tenant {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String logoUrl;
    private String domain;
    private Integer status;
    private String aiConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
