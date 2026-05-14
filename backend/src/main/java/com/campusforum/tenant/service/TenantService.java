package com.campusforum.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantMapper tenantMapper;

    public List<Tenant> listAll(String keyword, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Tenant> qw = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Tenant::getCode, keyword)
                    .or().like(Tenant::getName, keyword));
        }
        if (cursor != null) {
            qw.lt(Tenant::getId, cursor);
        }
        qw.orderByDesc(Tenant::getId);
        qw.last("LIMIT " + size);
        return tenantMapper.selectList(qw);
    }

    @Transactional
    public Tenant create(String code, String name, String domain) {
        LambdaQueryWrapper<Tenant> qw = new LambdaQueryWrapper<>();
        qw.eq(Tenant::getCode, code);
        if (tenantMapper.selectCount(qw) > 0) {
            throw new BusinessException(40000, "租户编码已存在");
        }
        Tenant tenant = new Tenant();
        tenant.setCode(code);
        tenant.setName(name);
        tenant.setDomain(domain);
        tenant.setStatus(1);
        tenantMapper.insert(tenant);
        log.info("Tenant created: id={}, code={}", tenant.getId(), code);
        return tenant;
    }

    @Transactional
    public Tenant update(Long id, String name, String domain, String logoUrl, String announcement) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(40000, "租户不存在");
        }
        if (name != null && !name.isBlank()) tenant.setName(name);
        if (domain != null) tenant.setDomain(domain);
        if (logoUrl != null) tenant.setLogoUrl(logoUrl);
        if (announcement != null) tenant.setAnnouncement(announcement);
        tenantMapper.updateById(tenant);
        return tenant;
    }

    @Transactional
    public void toggleStatus(Long id) {
        Tenant tenant = tenantMapper.selectById(id);
        if (tenant == null) {
            throw new BusinessException(40000, "租户不存在");
        }
        tenant.setStatus(tenant.getStatus() == 1 ? 0 : 1);
        tenantMapper.updateById(tenant);
        log.info("Tenant {} status changed to {}", id, tenant.getStatus());
    }

    public Map<String, Object> getAiConfig(Long tenantId) {
        Tenant t = tenantMapper.selectById(tenantId);
        Map<String, Object> defaults = Map.of("provider", "mock", "baseUrl", "", "apiKey", "", "model", "");
        if (t == null || t.getAiConfig() == null) return defaults;
        try {
            Map<String, Object> cfg = new ObjectMapper().readValue(t.getAiConfig(), Map.class);
            Map<String, Object> result = new HashMap<>(defaults);
            result.putAll(cfg);
            return result;
        } catch (JsonProcessingException e) {
            return defaults;
        }
    }

    @Transactional
    public void updateAiConfig(Long tenantId, String provider, String baseUrl, String apiKey, String model) {
        Tenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw new BusinessException(40000, "租户不存在");
        Map<String, String> cfg = new LinkedHashMap<>();
        if (provider != null) cfg.put("provider", provider);
        if (baseUrl != null) cfg.put("baseUrl", baseUrl);
        if (apiKey != null) cfg.put("apiKey", apiKey);
        if (model != null) cfg.put("model", model);
        try {
            t.setAiConfig(new ObjectMapper().writeValueAsString(cfg));
        } catch (JsonProcessingException e) {
            throw new BusinessException(40000, "序列化 AI 配置失败");
        }
        tenantMapper.updateById(t);
    }
}
