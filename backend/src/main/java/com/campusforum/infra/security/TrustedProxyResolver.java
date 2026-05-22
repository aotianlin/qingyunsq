package com.campusforum.infra.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * 客户端真实 IP 解析器：
 * <p>仅当请求 remoteAddr 命中 {@link SecurityProperties#getTrustedProxies()} 时，
 * 才采信 X-Forwarded-For / X-Real-IP；否则一律使用 {@link HttpServletRequest#getRemoteAddr()}，
 * 避免攻击者通过伪造头绕过 IP 维度限流。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustedProxyResolver {

    private final SecurityProperties properties;

    /** 预解析后的可信网段列表，避免每次请求都做字符串解析。 */
    private final List<CidrRange> trustedRanges = new ArrayList<>();

    @PostConstruct
    void init() {
        for (String entry : properties.getTrustedProxies()) {
            try {
                trustedRanges.add(CidrRange.parse(entry));
            } catch (Exception e) {
                log.warn("Invalid trusted proxy entry skipped: {}", entry);
            }
        }
        log.info("TrustedProxyResolver initialized with {} ranges", trustedRanges.size());
    }

    /**
     * 解析客户端真实 IP。
     *
     * @param request 当前请求
     * @return 真实 IP，失败时回退到 {@code request.getRemoteAddr()}
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!isFromTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }
        // 上游确实是可信代理，才接受其转发头
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isBlank()) {
            // 取最左侧的客户端原始 IP；其余 hop 由代理写入
            String first = header.split(",")[0].trim();
            if (!first.isEmpty() && !"unknown".equalsIgnoreCase(first)) {
                return first;
            }
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank() && !"unknown".equalsIgnoreCase(realIp)) {
            return realIp;
        }
        return remoteAddr;
    }

    private boolean isFromTrustedProxy(String addr) {
        if (addr == null || addr.isBlank()) return false;
        try {
            InetAddress inet = InetAddress.getByName(addr);
            for (CidrRange range : trustedRanges) {
                if (range.contains(inet)) return true;
            }
        } catch (UnknownHostException ignored) {
        }
        return false;
    }

    /** 简化的 CIDR 范围匹配，仅支持 IPv4/IPv6 单 IP 与 IPv4 CIDR。 */
    private static final class CidrRange {
        private final byte[] base;
        private final int prefixLength;

        private CidrRange(byte[] base, int prefixLength) {
            this.base = base;
            this.prefixLength = prefixLength;
        }

        static CidrRange parse(String entry) throws UnknownHostException {
            int slash = entry.indexOf('/');
            if (slash < 0) {
                InetAddress addr = InetAddress.getByName(entry.trim());
                return new CidrRange(addr.getAddress(), addr.getAddress().length * 8);
            }
            String ip = entry.substring(0, slash).trim();
            int prefix = Integer.parseInt(entry.substring(slash + 1).trim());
            InetAddress addr = InetAddress.getByName(ip);
            return new CidrRange(addr.getAddress(), prefix);
        }

        boolean contains(InetAddress addr) {
            byte[] candidate = addr.getAddress();
            if (candidate.length != base.length) return false;
            int fullBytes = prefixLength / 8;
            int remainderBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != base[i]) return false;
            }
            if (remainderBits == 0) return true;
            int mask = 0xFF & (0xFF << (8 - remainderBits));
            return (candidate[fullBytes] & mask) == (base[fullBytes] & mask);
        }
    }
}
