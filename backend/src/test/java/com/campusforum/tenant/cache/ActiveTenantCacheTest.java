package com.campusforum.tenant.cache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ActiveTenantCache 单元测试：命中/未命中/失效后再读。
 *
 * **Validates: Property 2** — multi 模式下通过 TenantResolutionFilter 的请求，
 * 其 TenantContext 持有的 tenantId 必属于 T_active（tenants.status=1）。
 * ActiveTenantCache 是实现该属性的核心组件。
 */
@ExtendWith(MockitoExtension.class)
class ActiveTenantCacheTest {

    @Mock
    private TenantMapper tenantMapper;

    private ActiveTenantCache cache;

    @BeforeEach
    void setUp() {
        TenantProperties props = new TenantProperties();
        TenantProperties.Cache cacheProps = new TenantProperties.Cache();
        cacheProps.setMaxSize(100);
        cacheProps.setTtl(Duration.ofSeconds(60));
        props.setCache(cacheProps);

        cache = new ActiveTenantCache(tenantMapper, props);
        cache.init();
    }

    // --- isActive ---

    @Test
    void isActive_shouldReturnTrueForActiveTenant() {
        Tenant tenant = createTenant(1L, "campus-a", 1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        assertThat(cache.isActive(1L)).isTrue();
    }

    @Test
    void isActive_shouldReturnFalseForInactiveTenant() {
        Tenant tenant = createTenant(2L, "campus-b", 0);
        when(tenantMapper.selectById(2L)).thenReturn(tenant);

        assertThat(cache.isActive(2L)).isFalse();
    }

    @Test
    void isActive_shouldReturnFalseForNonExistentTenant() {
        when(tenantMapper.selectById(99L)).thenReturn(null);

        assertThat(cache.isActive(99L)).isFalse();
    }

    @Test
    void isActive_shouldCacheResultAndNotQueryAgain() {
        Tenant tenant = createTenant(1L, "campus-a", 1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        // 第一次调用触发加载
        cache.isActive(1L);
        // 第二次调用应命中缓存
        cache.isActive(1L);

        verify(tenantMapper, times(1)).selectById(1L);
    }

    // --- findIdByCode ---

    @Test
    void findIdByCode_shouldReturnIdForActiveCode() {
        Tenant tenant = createTenant(3L, "fudan", 1);
        when(tenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tenant);

        Optional<Long> result = cache.findIdByCode("fudan");

        assertThat(result).isPresent().contains(3L);
    }

    @Test
    void findIdByCode_shouldReturnEmptyForUnknownCode() {
        when(tenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Optional<Long> result = cache.findIdByCode("unknown");

        assertThat(result).isEmpty();
    }

    @Test
    void findIdByCode_shouldCacheResultAndNotQueryAgain() {
        Tenant tenant = createTenant(3L, "fudan", 1);
        when(tenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tenant);

        cache.findIdByCode("fudan");
        cache.findIdByCode("fudan");

        verify(tenantMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    // --- getCode ---

    @Test
    void getCode_shouldReturnCodeForActiveTenant() {
        Tenant tenant = createTenant(1L, "campus-a", 1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        assertThat(cache.getCode(1L)).isEqualTo("campus-a");
    }

    @Test
    void getCode_shouldReturnNullForInactiveTenant() {
        Tenant tenant = createTenant(2L, "campus-b", 0);
        when(tenantMapper.selectById(2L)).thenReturn(tenant);

        assertThat(cache.getCode(2L)).isNull();
    }

    @Test
    void getCode_shouldReturnNullForNonExistentTenant() {
        when(tenantMapper.selectById(99L)).thenReturn(null);

        assertThat(cache.getCode(99L)).isNull();
    }

    // --- evict ---

    @Test
    void evict_shouldClearIdCacheAndReloadOnNextAccess() {
        Tenant tenant = createTenant(1L, "campus-a", 1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        // 加载缓存
        cache.isActive(1L);
        verify(tenantMapper, times(1)).selectById(1L);

        // 失效
        cache.evict(1L, "campus-a");

        // 再次访问应重新加载
        cache.isActive(1L);
        verify(tenantMapper, times(2)).selectById(1L);
    }

    @Test
    void evict_shouldClearCodeCacheAndReloadOnNextAccess() {
        Tenant tenant = createTenant(3L, "fudan", 1);
        when(tenantMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(tenant);

        // 加载缓存
        cache.findIdByCode("fudan");
        verify(tenantMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));

        // 失效
        cache.evict(3L, "fudan");

        // 再次访问应重新加载
        cache.findIdByCode("fudan");
        verify(tenantMapper, times(2)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void evict_shouldHandleNullCodeGracefully() {
        Tenant tenant = createTenant(1L, "campus-a", 1);
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        cache.isActive(1L);
        // code 为 null 时只清除 idCache
        cache.evict(1L, null);

        cache.isActive(1L);
        verify(tenantMapper, times(2)).selectById(1L);
    }

    // --- helper ---

    private Tenant createTenant(Long id, String code, Integer status) {
        Tenant t = new Tenant();
        t.setId(id);
        t.setCode(code);
        t.setStatus(status);
        return t;
    }
}
