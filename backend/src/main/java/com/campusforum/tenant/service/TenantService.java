package com.campusforum.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.infra.security.crypto.CryptoService;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    /** AI API Key 加密用的 HKDF purpose 标识，与其他场景密钥分域。 */
    private static final String CRYPTO_PURPOSE_AI = "tenant-ai-key";

    /** AI 配置 JSON 中的加密版本字段；2 表示新版 AES-GCM，缺失或 1 视为旧版 ECB。 */
    private static final String ENC_VERSION_FIELD = "encVersion";

    /** 当前默认加密版本号，每次升级算法递增。 */
    private static final int CURRENT_ENC_VERSION = 2;

    private final TenantMapper tenantMapper;
    private final CryptoService cryptoService;

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
     *
     * <p>解密策略：根据 {@code encVersion} 字段路由：</p>
     * <ul>
     *   <li>{@code encVersion=2}：使用新版 {@link CryptoService} 解密；</li>
     *   <li>{@code encVersion} 缺失或 {@code 1}：使用旧版 ECB 兼容解密，并异步重新加密为 v2；</li>
     * </ul>
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
                int version = parseEncVersion(cfg.get(ENC_VERSION_FIELD));
                String plain = decryptApiKeyByVersion(enc, version);
                credentials.put("apiKey", plain);
                credentials.remove(ENC_VERSION_FIELD); // 内部 credentials map 不暴露版本字段
                // 旧版本：触发异步重新加密为 v2，下次解密直接走新分支
                if (version < CURRENT_ENC_VERSION) {
                    asyncReencryptApiKey(tenantId, plain);
                }
            }
        } catch (JsonProcessingException ignored) {
        }
        return credentials;
    }

    /** 根据加密版本号选择解密路径。 */
    private String decryptApiKeyByVersion(String enc, int version) {
        if (version >= CURRENT_ENC_VERSION) {
            return cryptoService.decrypt(enc, CRYPTO_PURPOSE_AI);
        }
        // v1 / 缺失：旧 ECB 解密
        return cryptoService.decryptLegacyEcb(enc);
    }

    private int parseEncVersion(Object raw) {
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return 1;
    }

    /**
     * 异步将 v1 ECB 密文升级为 v2 GCM。失败仅日志告警，不影响主流程。
     * 单次升级在事务外执行，避免长事务阻塞调用方。
     */
    @Async
    public void asyncReencryptApiKey(Long tenantId, String plainApiKey) {
        try {
            Tenant t = tenantMapper.selectById(tenantId);
            if (t == null || t.getAiConfig() == null) return;
            Map<String, Object> cfg = new ObjectMapper().readValue(t.getAiConfig(), Map.class);
            cfg.put("apiKey", cryptoService.encrypt(plainApiKey, CRYPTO_PURPOSE_AI));
            cfg.put(ENC_VERSION_FIELD, CURRENT_ENC_VERSION);
            t.setAiConfig(new ObjectMapper().writeValueAsString(cfg));
            tenantMapper.updateById(t);
            log.info("Tenant {} AI apiKey upgraded to encVersion={}", tenantId, CURRENT_ENC_VERSION);
        } catch (Exception e) {
            // 重加密失败不抛异常，下次解密时仍可通过旧路径兜底
            log.warn("Tenant {} AI apiKey re-encrypt failed: {}", tenantId, e.getMessage());
        }
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
        // cfg 用 Object 值类型，兼容 encVersion (int) 与其他字符串字段共存
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (t.getAiConfig() != null && !t.getAiConfig().isBlank()) {
            try {
                Map<String, Object> existing = new ObjectMapper().readValue(t.getAiConfig(), Map.class);
                cfg.putAll(existing);
            } catch (JsonProcessingException ignored) {}
        }
        if (provider != null) cfg.put("provider", provider);
        if (baseUrl != null) cfg.put("baseUrl", baseUrl);
        // apiKey 仅在显式提供且非空时才更新；空字符串视作"保留原值"
        if (apiKey != null && !apiKey.isBlank()) {
            cfg.put("apiKey", cryptoService.encrypt(apiKey, CRYPTO_PURPOSE_AI));
            cfg.put(ENC_VERSION_FIELD, CURRENT_ENC_VERSION);
        }
        if (model != null) cfg.put("model", model);
        try {
            t.setAiConfig(new ObjectMapper().writeValueAsString(cfg));
        } catch (JsonProcessingException e) {
            throw new BusinessException(40000, "序列化 AI 配置失败");
        }
        tenantMapper.updateById(t);
    }
}
