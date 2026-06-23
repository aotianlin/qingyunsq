package com.campusforum.security;

import com.campusforum.infra.security.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityProperties 配置绑定单元测试。
 *
 * <p>验证 T1.2 新增字段（{@link SecurityProperties.Docs}、
 * {@link SecurityProperties.Crypto#getLegacyCutoverDate()}、
 * {@link SecurityProperties.WsTicket#getEnforcedCutoverDate()}、
 * {@link SecurityProperties.Upload#getBlockedMimeTypes()}、
 * {@link SecurityProperties.Upload#getSelfHosts()}）的默认值与
 * Spring Boot {@code Binder} 反序列化能力。</p>
 *
 * <p>使用纯 {@code Binder} 单元测试而非 {@code @SpringBootTest}，
 * 避免在缺失 MySQL/Redis 的环境下启动完整上下文，保证测试可在本地与 CI 快速跑通。</p>
 */
class SecurityPropertiesBindingTest {

    /**
     * 用空属性源调用 Binder，得到的对象等价于直接 {@code new SecurityProperties()}，
     * 用于校验各字段的默认值。
     */
    private SecurityProperties bindEmpty() {
        return new Binder(new MapConfigurationPropertySource(new HashMap<>()))
                .bindOrCreate("security", SecurityProperties.class);
    }

    /**
     * 用给定 key-value map 构造一个属性源并绑定到 {@link SecurityProperties}。
     */
    private SecurityProperties bindWith(Map<String, Object> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("security", SecurityProperties.class);
    }

    @Test
    @DisplayName("docs.enabled-profiles 默认应包含 dev 与 test")
    void docs_default_includes_dev_test() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.getDocs()).isNotNull();
        assertThat(props.getDocs().getEnabledProfiles())
                .containsExactlyInAnyOrder("dev", "test");

        // 同时通过 Binder 路径再校验一次，确认默认值在配置绑定流程中也成立
        SecurityProperties bound = bindEmpty();
        assertThat(bound.getDocs().getEnabledProfiles())
                .containsExactlyInAnyOrder("dev", "test");
    }

    @Test
    @DisplayName("application.yml default CORS origins include dev port 3001")
    void cors_defaultOrigins_includeDevPort3001() throws Exception {
        MockEnvironment env = new MockEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application", new ClassPathResource("application.yml"))
                .forEach(propertySource -> env.getPropertySources().addLast(propertySource));

        assertThat(env.getProperty("security.cors.allowed-origins"))
                .contains(
                        "http://localhost:3000",
                        "http://127.0.0.1:3000",
                        "http://localhost:3001",
                        "http://127.0.0.1:3001"
                );
    }

    @Test
    @DisplayName("crypto.legacy-cutover-date 可绑定为 LocalDate")
    void crypto_legacyCutoverDate_canBeBound() {
        Map<String, Object> map = new HashMap<>();
        map.put("security.crypto.legacy-cutover-date", "2026-09-01");

        SecurityProperties props = bindWith(map);

        assertThat(props.getCrypto().getLegacyCutoverDate())
                .isEqualTo(LocalDate.of(2026, 9, 1));

        // 默认情况下（未配置）应为 null，表示未设定 cutover
        SecurityProperties defaults = bindEmpty();
        assertThat(defaults.getCrypto().getLegacyCutoverDate()).isNull();
    }

    @Test
    @DisplayName("ws-ticket.enforced-cutover-date 可绑定为 LocalDate")
    void wsTicket_enforcedCutoverDate_canBeBound() {
        Map<String, Object> map = new HashMap<>();
        map.put("security.ws-ticket.enforced-cutover-date", "2026-07-01");

        SecurityProperties props = bindWith(map);

        assertThat(props.getWsTicket().getEnforcedCutoverDate())
                .isEqualTo(LocalDate.of(2026, 7, 1));

        // 默认情况下（未配置）应为 null
        SecurityProperties defaults = bindEmpty();
        assertThat(defaults.getWsTicket().getEnforcedCutoverDate()).isNull();
    }

    @Test
    @DisplayName("upload.blocked-mime-types 默认应为空列表")
    void upload_blockedMimeTypes_defaultsToEmpty() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.getUpload().getBlockedMimeTypes())
                .isNotNull()
                .isEmpty();

        // 通过 Binder 显式配置后应正确反序列化为多元素列表
        Map<String, Object> map = new HashMap<>();
        map.put("security.upload.blocked-mime-types[0]", "application/x-php");
        map.put("security.upload.blocked-mime-types[1]", "application/x-msdownload");

        SecurityProperties bound = bindWith(map);
        assertThat(bound.getUpload().getBlockedMimeTypes())
                .containsExactly("application/x-php", "application/x-msdownload");
    }

    @Test
    @DisplayName("upload.self-hosts 默认应为空列表")
    void upload_selfHosts_defaultsToEmpty() {
        SecurityProperties props = new SecurityProperties();

        assertThat(props.getUpload().getSelfHosts())
                .isNotNull()
                .isEmpty();

        // 通过 Binder 显式配置后应正确反序列化
        Map<String, Object> map = new HashMap<>();
        map.put("security.upload.self-hosts[0]", "minio.campus.example.com");

        SecurityProperties bound = bindWith(map);
        assertThat(bound.getUpload().getSelfHosts())
                .containsExactly("minio.campus.example.com");
    }
}
