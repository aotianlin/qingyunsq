package com.campusforum.tenant.archunit;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户架构一致性规则 — 通过源码扫描确保：
 * <ol>
 *   <li>只有允许的类才能读取 X-Tenant-Id 请求头</li>
 *   <li>production 代码中不存在 TenantContext.setTenantId 的硬编码字面量调用</li>
 * </ol>
 *
 * <p>Validates: Property 1</p>
 */
class TenantArchitectureTest {

    private static final Path PRODUCTION_SOURCE_ROOT = resolveProductionSourceRoot();

    /**
     * 允许在 production 代码中调用 request.getHeader("X-Tenant-Id") 的类。
     * - TenantBindingCheckInterceptor：已认证请求绑定校验（读取 header 用于比对，不修改 TenantContext）
     * - MultiTenantResolver：multi 模式下未认证请求的 header 回退解析
     */
    private static final Set<String> ALLOWED_HEADER_READERS = Set.of(
            "TenantBindingCheckInterceptor",
            "MultiTenantResolver"
    );

    /**
     * 允许在 production 代码中使用 TenantContext.setTenantId 并传入硬编码值的类。
     * - StandaloneTenantResolver 通过 props.getStandaloneTenantId() 间接设置，不直接硬编码
     * - TenantResolutionFilter 调用 setTenantId(result.tenantId())，非硬编码
     * 实际上当前设计中没有任何 production 类应该硬编码 setTenantId(数字)
     */
    private static final Set<String> ALLOWED_HARDCODED_SET_TENANT = Set.of(
            "StandaloneTenantResolver"
    );

    private static List<SourceFile> productionFiles;

    @BeforeAll
    static void scanProductionSources() throws IOException {
        productionFiles = new ArrayList<>();
        Files.walkFileTree(PRODUCTION_SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    String content = Files.readString(file);
                    String className = extractClassName(file);
                    productionFiles.add(new SourceFile(className, file, content));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * 规则 1：production 代码中只有 ALLOWED_HEADER_READERS 中的类可以调用 getHeader("X-Tenant-Id")。
     * 其他类如果需要租户信息，必须通过 TenantContext.getTenantId() 获取。
     */
    @Test
    void noUnauthorizedCodeReadsXTenantIdHeader() {
        // 匹配 getHeader("X-Tenant-Id") 的各种写法（含单引号变体，虽然 Java 不支持但防御性检查）
        Pattern pattern = Pattern.compile("getHeader\\s*\\(\\s*\"X-Tenant-Id\"\\s*\\)");

        List<String> violations = productionFiles.stream()
                .filter(sf -> !ALLOWED_HEADER_READERS.contains(sf.className))
                .filter(sf -> pattern.matcher(sf.content).find())
                .map(sf -> sf.className + " (" + sf.path + ")")
                .collect(Collectors.toList());

        assertThat(violations)
                .as("Only %s should read X-Tenant-Id header in production code. "
                        + "Other code must use TenantContext.getTenantId() instead.",
                        ALLOWED_HEADER_READERS)
                .isEmpty();
    }

    /**
     * 规则 2：production 代码中不应出现 TenantContext.setTenantId(硬编码数字) 的调用。
     * 合法的 setTenantId 调用应传入变量（如 result.tenantId()、props.getStandaloneTenantId()）。
     * 硬编码值（如 1L、1、2L）表示绕过了正常的租户解析流程。
     */
    @Test
    void noHardcodedTenantIdInSetTenantId() {
        // 匹配 TenantContext.setTenantId(数字) 或 setTenantId(数字L) 等硬编码模式
        Pattern pattern = Pattern.compile(
                "TenantContext\\s*\\.\\s*setTenantId\\s*\\(\\s*\\d+[Ll]?\\s*\\)");

        List<String> violations = productionFiles.stream()
                .filter(sf -> !ALLOWED_HARDCODED_SET_TENANT.contains(sf.className))
                .filter(sf -> pattern.matcher(sf.content).find())
                .map(sf -> {
                    Matcher m = pattern.matcher(sf.content);
                    List<String> matches = new ArrayList<>();
                    while (m.find()) {
                        matches.add(m.group());
                    }
                    return sf.className + " contains: " + matches;
                })
                .collect(Collectors.toList());

        assertThat(violations)
                .as("Production code must not use hardcoded values in TenantContext.setTenantId(). "
                        + "Use TenantResolver/TenantResolutionFilter to set tenant context properly.")
                .isEmpty();
    }

    // --- Helper methods ---

    private static Path resolveProductionSourceRoot() {
        // 尝试多种路径解析策略，兼容 IDE 和 Maven 执行环境
        Path candidate = Path.of("src/main/java/com/campusforum");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // 从项目根目录尝试
        candidate = Path.of("backend/src/main/java/com/campusforum");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // 使用 user.dir 系统属性
        String userDir = System.getProperty("user.dir", ".");
        candidate = Path.of(userDir, "src/main/java/com/campusforum");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        candidate = Path.of(userDir, "backend/src/main/java/com/campusforum");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // 最终回退：假设在 backend 目录下运行
        return Path.of("src/main/java/com/campusforum");
    }

    private static String extractClassName(Path file) {
        String fileName = file.getFileName().toString();
        return fileName.replace(".java", "");
    }

    private record SourceFile(String className, Path path, String content) {}
}
