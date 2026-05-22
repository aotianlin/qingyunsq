package com.campusforum.infra.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 启动期安全相关配置校验器。
 *
 * <p>校验规则：
 * <ul>
 *   <li>{@code security.crypto.master-key} 长度需 ≥ 32 字节，否则启动失败</li>
 *   <li>{@code security.crypto.legacy-mode=true} 时仅打印 WARN 提示，允许通过（紧急回滚场景）</li>
 *   <li>{@code security.signed-url-secret} 不允许在生产保留默认值（默认值检查放在文档约束中，启动只校验非空）</li>
 * </ul>
 * </p>
 *
 * <p>该校验器优先级低于 {@link com.campusforum.tenant.TenantStartupValidator}，
 * 即先确保租户基础设施 ready，再做安全配置校验，方便排查时按顺序定位问题。</p>
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SecurityStartupValidator implements ApplicationRunner {

    /** AES-GCM 主密钥最小长度（字节）。 */
    private static final int MIN_MASTER_KEY_BYTES = 32;

    private final SecurityProperties props;

    @Override
    public void run(ApplicationArguments args) {
        validateCrypto();
        validateSignedUrlSecret();
    }

    private void validateCrypto() {
        SecurityProperties.Crypto cfg = props.getCrypto();
        String key = cfg.getMasterKey();
        if (cfg.isLegacyMode()) {
            log.warn("SecurityStartupValidator: crypto legacy-mode is ENABLED — 仅紧急回滚使用，请尽快恢复");
            return;
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "security.crypto.master-key 未配置。请通过环境变量 CRYPTO_MASTER_KEY 注入 ≥ 32 字节随机字符串");
        }
        int byteLen = key.getBytes(StandardCharsets.UTF_8).length;
        if (byteLen < MIN_MASTER_KEY_BYTES) {
            throw new IllegalStateException(
                    "security.crypto.master-key 长度 " + byteLen + " 字节不足，需 ≥ " + MIN_MASTER_KEY_BYTES);
        }
        log.info("SecurityStartupValidator: crypto master-key length={} bytes OK", byteLen);
    }

    private void validateSignedUrlSecret() {
        String s = props.getSignedUrlSecret();
        if (s == null || s.isBlank()) {
            throw new IllegalStateException("security.signed-url-secret 未配置");
        }
        if (s.contains("please-override")) {
            log.warn("SecurityStartupValidator: signed-url-secret 仍为默认值，生产部署请通过 SIGNED_URL_SECRET 覆盖");
        }
    }
}
