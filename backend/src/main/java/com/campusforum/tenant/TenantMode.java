package com.campusforum.tenant;

/**
 * 租户运行模式。
 * <ul>
 *   <li>STANDALONE — 单租户模式，所有请求使用固定 tenant_id（默认 1）</li>
 *   <li>MULTI — 多租户模式，通过子域名 / X-Tenant-Id / Sa-Token Session 解析租户</li>
 * </ul>
 */
public enum TenantMode {
    /** 单租户模式：忽略客户端传入的任何租户标识，始终使用 standaloneTenantId */
    STANDALONE,
    /** 多租户模式：按优先级从 Sa-Token Session、子域名、X-Tenant-Id 解析租户 */
    MULTI
}
