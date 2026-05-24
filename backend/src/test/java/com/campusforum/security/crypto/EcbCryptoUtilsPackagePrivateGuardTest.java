package com.campusforum.security.crypto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code EcbCryptoUtils} 可见性边界守卫测试。
 *
 * <p>对应任务 T1.1（bugfix.md 漏洞 1）：业务代码必须无法直接调用旧 ECB
 * 解密实现，所有访问都必须经过 {@code CryptoService.decryptLegacyEcb} 网关，
 * 才能确保走到 metrics 埋点 + 失败抛异常的统一治理路径。</p>
 *
 * <p>该 spec 通过 reflection 验证类与方法的访问修饰位：</p>
 * <ul>
 *   <li>类不带 {@link Modifier#PUBLIC} 标志（即 package-private）；</li>
 *   <li>类带 {@link Modifier#FINAL} 标志，禁止被继承绕过；</li>
 *   <li>{@code decrypt} 方法不带 {@link Modifier#PUBLIC} / {@link Modifier#PROTECTED}，
 *       也是 package-private 静态方法；</li>
 *   <li>原始 {@code encrypt} 方法已被删除（兼容期没有任何代码路径需要写入新 ECB 密文）。</li>
 * </ul>
 */
class EcbCryptoUtilsPackagePrivateGuardTest {

    /** 被测目标类的全限定名（不能直接 import，因此用字符串）。 */
    private static final String FQCN =
            "com.campusforum.infra.security.crypto.legacy.EcbCryptoUtils";

    @Test
    void class_should_be_package_private_and_final() throws Exception {
        Class<?> clazz = Class.forName(FQCN);
        int modifiers = clazz.getModifiers();

        // 关键守卫：public 标志必须不存在，业务代码无法 import
        assertThat(Modifier.isPublic(modifiers))
                .as("EcbCryptoUtils 必须是 package-private（不带 public），否则任意业务代码都能直接调用旧密钥实现")
                .isFalse();

        // 兜底守卫：禁止被继承绕过 package 级访问限制
        assertThat(Modifier.isFinal(modifiers))
                .as("EcbCryptoUtils 必须是 final，避免子类化绕过可见性")
                .isTrue();
    }

    @Test
    void decrypt_method_should_be_package_private_static() throws Exception {
        Class<?> clazz = Class.forName(FQCN);
        Method method = clazz.getDeclaredMethod("decrypt", String.class);
        int modifiers = method.getModifiers();

        // decrypt 方法本身也必须是 package-private（不能 public / protected）
        assertThat(Modifier.isPublic(modifiers))
                .as("decrypt 方法必须是 package-private")
                .isFalse();
        assertThat(Modifier.isProtected(modifiers))
                .as("decrypt 方法必须是 package-private（不能 protected）")
                .isFalse();
        assertThat(Modifier.isStatic(modifiers))
                .as("decrypt 方法必须是 static（工具类无实例状态）")
                .isTrue();
    }

    @Test
    void encrypt_method_should_be_removed() throws Exception {
        Class<?> clazz = Class.forName(FQCN);

        // 兼容期没有任何代码路径需要写入新 ECB 密文，encrypt 必须被彻底删除
        boolean hasEncrypt = false;
        for (Method m : clazz.getDeclaredMethods()) {
            if ("encrypt".equals(m.getName())) {
                hasEncrypt = true;
                break;
            }
        }
        assertThat(hasEncrypt)
                .as("encrypt 方法必须被删除：兼容期不再写入新 ECB 密文")
                .isFalse();
    }
}
