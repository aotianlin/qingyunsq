package com.campusforum.security.url;

import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.SignedUrlService;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 任务 TPBT.5（部分）/ design.md Property 6：签名 URL 互逆与防伪。
 *
 * <p>验证：</p>
 * <ol>
 *   <li><b>互逆性</b>：随机 {@code (userId, type, resourceId, action, exp)}，
 *       {@code verify(sign(...), type, resourceId, action)} 在 exp 之前返回非 null 且字段一致；</li>
 *   <li><b>过期回收</b>：exp 已经过去时 {@code verify} 必须返回 null；</li>
 *   <li><b>类型 / action 不匹配</b>：HMAC 校验通过但传入的 expectedType / expectedAction 与
 *       payload 不一致时，必须返回 null（防止跨资源套娃签名）；</li>
 *   <li><b>篡改防御</b>：把 token 末位字符改掉后必须返回 null。</li>
 * </ol>
 */
class SignedUrlRoundTripPropertyTest {

    private SignedUrlService buildService() {
        SecurityProperties props = new SecurityProperties();
        props.setSignedUrlSecret("test-signed-url-secret-must-be-long-enough-aaaaaaaaaaaaaaaa");
        props.setSignedUrlTtlSeconds(60);
        return new SignedUrlService(props);
    }

    @Provide
    Arbitrary<String> types() {
        return Arbitraries.of("resource", "post", "ws-ticket", "user-asset");
    }

    @Provide
    Arbitrary<String> actions() {
        return Arbitraries.of("download", "preview", "connect", "view");
    }

    @Property(tries = 100)
    void sign_thenVerify_returnsOriginalFields(
            @ForAll @LongRange(min = 1, max = 1_000_000_000L) long userId,
            @ForAll("types") String type,
            @ForAll @LongRange(min = 1, max = 1_000_000_000L) long resourceId,
            @ForAll("actions") String action) {
        SignedUrlService svc = buildService();
        long exp = System.currentTimeMillis() / 1000 + 300; // 5 分钟后过期
        String token = svc.sign(userId, type, resourceId, action, exp);
        SignedUrlService.Verified v = svc.verify(token, type, resourceId, action);
        assertThat(v).isNotNull();
        assertThat(v.userId()).isEqualTo(userId);
        assertThat(v.type()).isEqualTo(type);
        assertThat(v.resourceId()).isEqualTo(resourceId);
        assertThat(v.action()).isEqualTo(action);
        assertThat(v.expiresAtSeconds()).isEqualTo(exp);
    }

    @Property(tries = 50)
    void expiredToken_verifyReturnsNull(
            @ForAll @LongRange(min = 1, max = 1_000_000L) long userId,
            @ForAll("types") String type,
            @ForAll @LongRange(min = 1, max = 1_000_000L) long resourceId,
            @ForAll("actions") String action) {
        SignedUrlService svc = buildService();
        long expPast = System.currentTimeMillis() / 1000 - 1; // 1 秒前已过期
        String token = svc.sign(userId, type, resourceId, action, expPast);
        assertThat(svc.verify(token, type, resourceId, action))
                .as("过期的 token 必须返回 null")
                .isNull();
    }

    @Property(tries = 50)
    void typeMismatch_returnsNull(
            @ForAll @LongRange(min = 1, max = 1_000_000L) long userId,
            @ForAll @LongRange(min = 1, max = 1_000_000L) long resourceId) {
        SignedUrlService svc = buildService();
        long exp = System.currentTimeMillis() / 1000 + 300;
        String token = svc.sign(userId, "resource", resourceId, "download", exp);
        // type 不匹配（虽然 HMAC 校验过）
        assertThat(svc.verify(token, "post", resourceId, "download")).isNull();
        // action 不匹配
        assertThat(svc.verify(token, "resource", resourceId, "preview")).isNull();
        // resourceId 不匹配
        assertThat(svc.verify(token, "resource", resourceId + 1, "download")).isNull();
    }

    @Test
    void tampered_token_returnsNull() {
        SignedUrlService svc = buildService();
        long exp = System.currentTimeMillis() / 1000 + 300;
        String token = svc.sign(1L, "resource", 100L, "download", exp);
        // 简单替换末位字符不一定能改变 base64 解码结果（同一字节可能由多个字符表示），
        // 这里直接修改签名段中部位置（payload 与 signature 之间的 '.' 后第 4 位），保证字节级 diff
        int dotIndex = token.indexOf('.');
        // 从 signature 段中部修改一字节
        int tamperPos = dotIndex + 4;
        char orig = token.charAt(tamperPos);
        // 选一个一定不同的 base64url 字母
        char other = (orig == 'a') ? 'b' : 'a';
        String tampered = token.substring(0, tamperPos) + other + token.substring(tamperPos + 1);
        assertThat(svc.verify(tampered, "resource", 100L, "download")).isNull();
    }

    @Test
    void malformedToken_returnsNull() {
        SignedUrlService svc = buildService();
        // 各种坏 token 都必须安全返回 null，不抛异常
        assertThat(svc.verify(null, "resource", 1L, "download")).isNull();
        assertThat(svc.verify("", "resource", 1L, "download")).isNull();
        assertThat(svc.verify("no-dot-no-format", "resource", 1L, "download")).isNull();
        assertThat(svc.verify("a.b.c", "resource", 1L, "download")).isNull();
        assertThat(svc.verify("invalid-base64.invalid-base64", "resource", 1L, "download")).isNull();
    }
}
