package com.campusforum.infra.security.crypto.legacy;

import com.campusforum.infra.security.CryptoException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 旧版 ECB 兼容解密工具（仅供历史 v1 数据迁移读取）。
 *
 * <p>对应 bugfix.md 漏洞 1：早期实现 {@code com.campusforum.common.CryptoUtils}
 * 用全局硬编码 16 字节密钥 + AES/ECB/PKCS5Padding 提供加解密能力，且解密
 * 失败时静默回退原始密文，污染加密链路完整性边界。本类是其收缩后的替代品：</p>
 *
 * <ul>
 *   <li><b>可见性收紧</b>：类与方法均为 package-private，仅同包内的
 *       {@link com.campusforum.infra.security.crypto.CryptoService} 可调用，
 *       任何业务代码尝试 {@code import} 该类都会编译失败，杜绝新调用方
 *       继续写入弱密钥密文。</li>
 *   <li><b>仅保留解密</b>：删除 {@code encrypt(...)}，兼容期没有任何代码
 *       路径需要写入新 ECB 密文，新写入统一走 {@code CryptoService.encrypt}
 *       （AES-GCM + HKDF）。</li>
 *   <li><b>失败必抛异常</b>：解密失败时抛出 {@link CryptoException}，
 *       绝不再返回原始密文，避免密文格式非法 / 密钥换了等情况伪装成
 *       "已解密"流入下游。</li>
 *   <li><b>显式 deprecated 标记</b>：{@link Deprecated#forRemoval()} 为 true，
 *       配合 {@code SecurityProperties.Crypto.legacyCutoverDate} 在迁移完成
 *       后由 {@code SecurityStartupValidator} 拒绝继续启动。</li>
 * </ul>
 *
 * <p>本类不维护实例状态，禁止实例化。</p>
 */
@Deprecated(forRemoval = true)
final class EcbCryptoUtils {

    /**
     * 旧版 ECB 兼容期硬编码密钥（仅供历史 v1 数据迁移读取）。
     *
     * <p>该值与历史 {@code CryptoUtils.DEFAULT_KEY} 完全一致，迁移完毕后
     * 随本类一并删除；新代码严禁再用此密钥写入任何新数据。</p>
     */
    private static final String DEFAULT_KEY = "CampusForum@1234";

    /** 标准 ECB 算法名（PKCS5Padding 与 PKCS7 在 16 字节块下行为一致）。 */
    private static final String CIPHER_ALGORITHM = "AES/ECB/PKCS5Padding";

    /** 工具类禁止实例化。 */
    private EcbCryptoUtils() {
    }

    /**
     * 解密旧版 ECB 密文。
     *
     * <p>失败语义与新版 {@code CryptoService.decrypt} 对齐：</p>
     * <ul>
     *   <li>入参为 {@code null} 或空白 → 抛 {@link CryptoException}。</li>
     *   <li>base64 解码失败 / 密文长度非 16 字节倍数 / 密钥不匹配 / padding
     *       异常等任何错误，**统一**抛 {@link CryptoException}，绝不返回
     *       原始密文。</li>
     * </ul>
     *
     * @param encrypted base64 编码的旧 ECB 密文
     * @return 解密后的明文（UTF-8 字符串）
     * @throws CryptoException 解密失败（包含入参非法 / 密文格式损坏 / 密钥不匹配）
     */
    static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) {
            // 直接拒绝空 / 空白入参，避免下游误把"空字符串"当成"已解密"
            throw new CryptoException("旧密文为空");
        }
        byte[] cipherBytes;
        try {
            // base64 解码：非法字符或长度非 4 倍数会抛 IllegalArgumentException
            cipherBytes = Base64.getDecoder().decode(encrypted);
        } catch (IllegalArgumentException e) {
            throw new CryptoException("旧密文 base64 格式非法");
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(
                    DEFAULT_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            // 长度非 16 字节倍数 / padding 错误 / 密钥不匹配等，统一在此抛异常
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 严禁回退原文：与漏洞 1 修复语义一致，全部转换为 CryptoException
            throw new CryptoException("旧密文解密失败");
        }
    }
}
