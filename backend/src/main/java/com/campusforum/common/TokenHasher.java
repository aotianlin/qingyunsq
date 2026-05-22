package com.campusforum.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 一次性 token 单向哈希工具。
 *
 * <p>用于密码重置令牌等场景：用户邮件中收到原始随机 token，服务端仅存 SHA-256 哈希。
 * 这样数据库泄漏时攻击者拿到的也只是 hash，无法直接重置账号。</p>
 *
 * <p>SHA-256 hex 输出固定 64 字符，与 {@code users.reset_token VARCHAR(64)} 长度一致。</p>
 */
public final class TokenHasher {

    private TokenHasher() {}

    /**
     * 计算输入字符串的 SHA-256 hex（小写）。
     *
     * @param input 任意字符串，null 直接返回 null
     * @return 64 字符 hex 字符串，或 null
     */
    public static String sha256Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JRE 必备算法，正常 JDK 不会抛
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
