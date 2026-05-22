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
import com.campusforum.common.CryptoUtils;

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
            // 不回传明文 API Key：仅指示是否已配置，避免管理员页面网络面板/日志泄漏第三方密钥
            Object stored = result.get("apiKey");
            boolean configured = stored instanceof String s && !s.isBlank();
            result.put("apiKey", "");
            result.put("apiKeyConfigured", configured);
            return result;
        } catch (JsonProcessingException e) {
            return defaults;
        }
    }

    /**
     * 仅供后端内部（AI 调用链路）使用，返回明文 baseUrl/apiKey/model。不要暴露给 controller。
     */
    public Map<String, String> resolveAiCredentials(Long tenantId) {
        Tenant t = tenantMapper.selectById(tenantId);
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("provider", "mock");
        credentials.put("baseUrl", "");
        credentials.put("apiKey", "");
        credentials.put("model", "");
        if (t == null || t.getAiConfig() == null) return credentials;
        try {
            Map<String, Object> cfg = new ObjectMapper().readValue(t.getAiConfig(), Map.class);
            for (Map.Entry<String, Object> entry : cfg.entrySet()) {
                if (entry.getValue() instanceof String s) {
                    credentials.put(entry.getKey(), s);
                }
            }
            String enc = credentials.get("apiKey");
            if (enc != null && !enc.isBlank()) {
                credentials.put("apiKey", CryptoUtils.decrypt(enc));
            }
        } catch (JsonProcessingException ignored) {
        }
        return credentials;
    }

    @Transactional
    public void updateAiConfig(Long tenantId, String provider, String baseUrl, String apiKey, String model) {
        Tenant t = tenantMapper.selectById(tenantId);
        if (t == null) throw new BusinessException(40000, "租户不存在");
        // 仅当 provider 为外部远程服务时校验 baseUrl 防 SSRF；mock/ollama-local 等本地形式由该校验放行
        if (baseUrl != null && !baseUrl.isBlank()
                && provider != null && "openai".equalsIgnoreCase(provider)) {
            try {
                com.campusforum.infra.security.PrivateNetworkValidator.requirePublic(baseUrl, true);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(40000, "AI baseUrl 不合法：" + ex.getMessage());
            }
        }

        // 在已存配置基础上做 merge，避免传 null 时清空已有字段（特别是 apiKey 留空表示"不修改"）
        Map<String, String> cfg = new LinkedHashMap<>();
        if (t.getAiConfig() != null && !t.getAiConfig().isBlank()) {
            try {
                Map<String, Object> existing = new ObjectMapper().readValue(t.getAiConfig(), Map.class);
                for (Map.Entry<String, Object> entry : existing.entrySet()) {
                    if (entry.getValue() instanceof String s) cfg.put(entry.getKey(), s);
                }
            } catch (JsonProcessingException ignored) {}
        }
        if (provider != null) cfg.put("provider", provider);
        if (baseUrl != null) cfg.put("baseUrl", baseUrl);
        // apiKey 仅在显式提供且非空时才更新；空字符串视作"保留原值"
        if (apiKey != null && !apiKey.isBlank()) cfg.put("apiKey", CryptoUtils.encrypt(apiKey));
        if (model != null) cfg.put("model", model);
        try {
            t.setAiConfig(new ObjectMapper().writeValueAsString(cfg));
        } catch (JsonProcessingException e) {
            throw new BusinessException(40000, "序列化 AI 配置失败");
        }
        tenantMapper.updateById(t);
    }
}
