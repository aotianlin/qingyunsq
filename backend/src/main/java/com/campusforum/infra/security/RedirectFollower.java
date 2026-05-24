package com.campusforum.infra.security;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * 手动跟随 HTTP redirect 的工具，每跳重新校验目标 host。
 *
 * <p>对应 bugfix.md 漏洞 23 的修复扩展点：{@link SafeHttpClient} 已经在
 * 连接维度禁用了 {@code HttpURLConnection.setInstanceFollowRedirects}，
 * 业务代码遇到 3xx 不再自动跳转。如果某个合法外联确实需要 follow 重定向，
 * 必须显式走本工具，由其在每跳前重新调用
 * {@link PrivateNetworkValidator#requirePublic(String, boolean)} 校验目标 host，
 * 防止重定向链中插入私网地址（云元数据 / DNS 重绑定后的内网 IP）。</p>
 *
 * <p>当前 OpenAiCompatService 已经按"遇到 redirect 即视为上游配置错误"
 * 的语义工作（DeepSeek / OpenAI 兼容服务正常情况都不会 redirect 到 CDN），
 * 因此本工具尚未被实际调用，作为安全扩展点保留：未来若有合法 follow 需求，
 * 调用方应显式调 {@link #follow(URI)}，而非直接用 RestTemplate 触发自动 redirect。</p>
 *
 * <p><b>限制</b>：</p>
 * <ul>
 *   <li>最多跟随 {@value #MAX_HOPS} 跳；</li>
 *   <li>每跳新 URI 都通过 {@link PrivateNetworkValidator#requirePublic(String, boolean)} 验证；</li>
 *   <li>检测到循环跳转（同一 URI 在历史中出现）即抛 {@link SSRFBlockedException}；</li>
 *   <li>当前实现仅校验链路安全性，不真正发起 HTTP 请求 —— 真正的 follow 逻辑
 *       要等到具体业务接入时再补上 RestTemplate / OkHttp 调用，本类先保证
 *       "host 校验链"是正确的。</li>
 * </ul>
 */
@Slf4j
public final class RedirectFollower {

    /** 最大跳数：超过即视为重定向风暴或恶意诱导，直接拒绝。 */
    public static final int MAX_HOPS = 3;

    private RedirectFollower() {}

    /**
     * 校验一个 URI 链是否安全可跟随。
     *
     * <p>本方法不发起 HTTP 请求，仅做"假设要 follow 这条链"的安全前置校验：</p>
     * <ol>
     *   <li>调用方传入的 URI 列表代表想要依次跟随的链，例如
     *       {@code [original, location1, location2]}；</li>
     *   <li>列表长度不可超过 {@link #MAX_HOPS} + 1（包含 original）；</li>
     *   <li>每个 URI 都执行 {@link PrivateNetworkValidator#requirePublic} 校验；</li>
     *   <li>若链中出现重复 URI（循环跳转）抛 {@link SSRFBlockedException}。</li>
     * </ol>
     *
     * <p>这是 redirect follow 的最小可验证单元；真正发起 HTTP 请求的逻辑
     * 由后续接入方在自己的业务代码里组装。</p>
     *
     * @param chain 想要依次跟随的 URI 序列，第一个元素是原始请求 URI
     * @throws SSRFBlockedException 链中任意 URI 命中私网 / 链路本地 / 元数据地址，
     *                              或链长度超限，或出现循环跳转
     */
    public static void assertChainIsPublic(URI... chain) {
        if (chain == null || chain.length == 0) {
            throw new SSRFBlockedException("RedirectFollower: 空链不允许跟随");
        }
        if (chain.length > MAX_HOPS + 1) {
            throw new SSRFBlockedException(
                    "RedirectFollower: 跳数超过上限 " + MAX_HOPS + "，链长度=" + chain.length);
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < chain.length; i++) {
            URI uri = chain[i];
            if (uri == null) {
                throw new SSRFBlockedException("RedirectFollower: 链中第 " + i + " 跳为 null");
            }
            String key = uri.toString();
            if (!seen.add(key)) {
                throw new SSRFBlockedException("RedirectFollower: 检测到循环跳转 → " + key);
            }
            // 漏洞 23：每跳前都做一次"必须公网"校验，覆盖原始 URI + 所有 Location
            PrivateNetworkValidator.requirePublic(key, true);
        }
        log.debug("RedirectFollower: 链路校验通过，hops={}", chain.length - 1);
    }

    /**
     * 单步拼接 URI 校验。便于业务代码每收到一次 Location 即调用一次。
     *
     * @param baseUri  当前请求 URI
     * @param location 服务端返回的 Location 头（可能是相对路径）
     * @return 解析后的下一跳绝对 URI
     * @throws SSRFBlockedException 解析失败或下一跳命中私网
     */
    public static URI nextHop(URI baseUri, String location) {
        if (baseUri == null || location == null || location.isBlank()) {
            throw new SSRFBlockedException("RedirectFollower: baseUri / location 不能为空");
        }
        try {
            URI next = baseUri.resolve(location);
            PrivateNetworkValidator.requirePublic(next.toString(), true);
            return next;
        } catch (IllegalArgumentException e) {
            throw new SSRFBlockedException("RedirectFollower: Location 解析失败 - " + e.getMessage());
        }
    }
}
