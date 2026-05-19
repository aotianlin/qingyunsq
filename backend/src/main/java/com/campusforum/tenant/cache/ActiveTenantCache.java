package com.campusforum.tenant.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Active 租户缓存，使用 Caffeine 实现短 TTL 的本地缓存。
 * 提供按 id 和 code 两种维度的查询，仅缓存 status=1 的活跃租户。
 */
@Component
@RequiredArgsConstructor
public class ActiveTenantCache {

    private final TenantMapper tenantMapper;
    private final TenantProperties props;

    private LoadingCache<Long, Optional<Tenant>> idCache;
    private LoadingCache<String, Optional<Tenant>> codeCache;

    @PostConstruct
    public void init() {
        Caffeine<Object, Object> base = Caffeine.newBuilder()
                .maximumSize(props.getCache().getMaxSize())
                .expireAfterWrite(props.getCache().getTtl());
        idCache = base.build(id ->
                Optional.ofNullable(tenantMapper.selectById(id))
                        .filter(t -> t.getStatus() != null && t.getStatus() == 1));
        codeCache = Caffeine.newBuilder()
                .maximumSize(props.getCache().getMaxSize())
                .expireAfterWrite(props.getCache().getTtl())
                .build(code ->
                        Optional.ofNullable(tenantMapper.selectOne(
                                new LambdaQueryWrapper<Tenant>()
                                        .eq(Tenant::getCode, code)
                                        .eq(Tenant::getStatus, 1))));
    }

    /**
     * 判断指定 tenantId 是否为活跃租户（status=1）
     */
    public boolean isActive(long tenantId) {
        return idCache.get(tenantId).isPresent();
    }

    /**
     * 根据租户 code 查找对应的 tenantId
     */
    public Optional<Long> findIdByCode(String code) {
        return codeCache.get(code).map(Tenant::getId);
    }

    /**
     * 根据 tenantId 获取租户 code，不存在或非活跃时返回 null
     */
    public String getCode(long tenantId) {
        return idCache.get(tenantId).map(Tenant::getCode).orElse(null);
    }

    /**
     * 租户停用/删除时由 TenantService 主动调用，清除缓存
     */
    public void evict(long id, String code) {
        idCache.invalidate(id);
        if (code != null) {
            codeCache.invalidate(code);
        }
    }
}
