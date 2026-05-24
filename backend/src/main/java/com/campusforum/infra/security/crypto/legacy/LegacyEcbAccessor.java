package com.campusforum.infra.security.crypto.legacy;

import com.campusforum.infra.security.CryptoException;

/**
 * 旧 ECB 解密的受控访问入口。
 *
 * <p>对应 bugfix.md 漏洞 1：核心实现 {@link EcbCryptoUtils} 是 package-private
 * final 类，业务代码无法直接 import；本类作为 {@code legacy} 包对外的**唯一**
 * 公开入口，仅供 {@code com.campusforum.infra.security.crypto.CryptoService}
 * 转发调用使用，请勿在业务代码 / controller / service 中直接引用。</p>
 *
 * <p>访问控制策略：</p>
 * <ul>
 *   <li>{@link EcbCryptoUtils} 类 + 方法保持 package-private，{@code Modifier.PUBLIC}
 *       标志位为 0，业务代码 import 即编译失败；</li>
 *   <li>本类带 {@code @Deprecated(forRemoval = true)}，调用方在编辑器中会
 *       看到删除线 + 警告，配合 SonarQube / IDE 提示帮助 reviewer 拦截
 *       不当调用；</li>
 *   <li>所有解密失败统一抛 {@link CryptoException}，与 {@link EcbCryptoUtils}
 *       内部失败语义对齐，绝不再回退原始密文。</li>
 * </ul>
 */
@Deprecated(forRemoval = true)
public final class LegacyEcbAccessor {

    /** 工具类禁止实例化。 */
    private LegacyEcbAccessor() {
    }

    /**
     * 转发调用 {@link EcbCryptoUtils#decrypt(String)}。
     *
     * <p>本方法仅供 {@code CryptoService.decryptLegacyEcb} 网关使用，
     * 网关层负责 metrics 埋点 + 失败统计；本类不再做任何额外日志，
     * 避免双重日志污染。</p>
     *
     * @param ciphertext base64 编码的旧 ECB 密文
     * @return 解密后的明文
     * @throws CryptoException 解密失败（入参非法 / 密文格式损坏 / 密钥不匹配）
     */
    public static String decrypt(String ciphertext) {
        return EcbCryptoUtils.decrypt(ciphertext);
    }
}
