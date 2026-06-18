package com.campusforum.user.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.audit.AuditContext;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.infra.email.EmailCodeScene;
import com.campusforum.infra.metrics.SecurityMetrics;
import com.campusforum.infra.security.LoginLockoutService;
import com.campusforum.infra.security.SecurityProperties;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.points.service.PointsService;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.user.config.StudentNoMappingProperties;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.PublicUserVO;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UpdateProfileRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /** 标签合法字符白名单：中英文/数字/下划线/连字符，长度 1-32（缺陷 1.4 加固）。 */
    private static final Pattern TAG_PATTERN =
            Pattern.compile("^[\\w\\u4e00-\\u9fa5\\-]{1,32}$");

    /** 角色权重映射，用于 changeRole / banUser 校验（缺陷 1.7 加固）。 */
    private static final java.util.Map<String, Integer> ROLE_WEIGHT = java.util.Map.of(
            "USER", 1,
            "TENANT_ADMIN", 2,
            "SUPER_ADMIN", 3
    );

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final StudentNoMappingProperties studentNoMapping;
    private final ActiveTenantCache activeTenantCache;
    private final LoginLockoutService loginLockoutService;
    private final EmailVerificationCodeService emailVerificationCodeService;
    private final TrustedProxyResolver trustedProxyResolver;
    private final HttpServletRequest httpRequest;
    private final SecurityProperties securityProperties;
    /** 审计日志服务（敏感凭证变更必须落审计）。 */
    private final AuditLogService auditLogService;
    /** 安全监控埋点（敏感凭证变更后强制踢下线计数）。 */
    private final SecurityMetrics securityMetrics;

    /**
     * 固定 BCrypt hash，仅用于用户不存在时消耗等量 CPU 时间，防止时序攻击。
     * 该值是一个合法的 BCrypt hash（cost=10），不对应任何真实密码。
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Transactional
    public UserVO register(RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());

        // 检查邮箱是否已注册
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该邮箱已注册");
        }

        // 检查学号是否重复（非空时）
        if (req.getStudentNo() != null && !req.getStudentNo().isBlank()) {
            if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getStudentNo, req.getStudentNo())) > 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该学号已注册");
            }
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt(10)));
        user.setStudentNo(req.getStudentNo());
        user.setNickname(req.getNickname());
        user.setRole("USER");
        user.setStatus(1);
        user.setPoints(0L);

        // 学号自动识别学院/专业/年级
        if (req.getStudentNo() != null && !req.getStudentNo().isBlank()) {
            for (StudentNoMappingProperties.MappingEntry entry : studentNoMapping.getMapping()) {
                if (req.getStudentNo().startsWith(entry.getPrefix())) {
                    user.setCollege(entry.getCollege());
                    user.setMajor(entry.getMajor());
                    user.setGrade(entry.getGrade());
                    break;
                }
            }
        }

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // selectCount 预检查与 insert 之间存在并发窗口，最终由 DB 唯一索引
            // (uk_tenant_email / uk_tenant_student) 兜底。命中时转换为友好的 400 提示，
            // 而非把底层异常以 500 形式抛给前端。
            log.warn("Register unique-key conflict for email={}: {}", email, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该邮箱或学号已注册");
        }

        // 验证码在 insert 成功之后才消费（删除 Redis key）：
        // - 验证码错误：verifyAndConsume 抛异常 → @Transactional 回滚刚插入的 user，
        //   且因为未删除 key，验证码不会被吞掉；
        // - 唯一索引并发冲突：在到达此行前已抛出，验证码同样不会被消费。
        // 这样保证"注册失败时验证码可复用"，避免用户被迫反复获取验证码。
        emailVerificationCodeService.verifyAndConsume(email, EmailCodeScene.REGISTER, req.getEmailCode());

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());
        return toVO(user);
    }

    public void sendEmailCode(String email, EmailCodeScene scene) {
        emailVerificationCodeService.sendCode(email, scene);
    }

    /**
     * 判断当前租户下指定邮箱是否已注册（不区分账号是否被禁用）。
     * 仅用于登录页"获取验证码"前的防呆提示。
     * 安全说明：此接口存在轻度邮箱枚举风险，已在 application.yml 中配置严格限流（与 login 同级），
     * 且 register 接口本身就具备等价枚举能力，故未增加新的实质攻击面。
     */
    public boolean existsByEmail(String email) {
        long tid = requireTenantId();
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isEmpty()) return false;
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tid)
                .eq(User::getEmail, normalizedEmail)) > 0;
    }


    public UserVO login(LoginRequest req) {
        if ("CODE".equalsIgnoreCase(req.getLoginType())) {
            return loginByEmailCode(req);
        }
        return loginByPassword(req);
    }

    private UserVO loginByPassword(LoginRequest req) {
        long tid = requireTenantId();
        String email = normalizeEmail(req.getEmail());
        String password = req.getPassword();
        // 解析真实 IP（仅在可信代理后相信 X-Forwarded-For），用于 IP 维度锁定校验
        String clientIp = trustedProxyResolver.resolve(httpRequest);
        // 安全加固（缺陷 1.15）：先校验 IP 维度锁定，再校验账号维度锁定
        loginLockoutService.ensureIpNotLocked(clientIp);
        loginLockoutService.ensureNotLocked(tid, email);
        User user = findTenantUser(tid, email);

        boolean ok;
        if (user == null) {
            // 防时序攻击：用户不存在时仍执行一次 BCrypt 校验，消耗等量 CPU 时间
            BCrypt.checkpw(StringUtils.hasText(password) ? password : "invalid-password", DUMMY_BCRYPT_HASH);
            ok = false;
        } else if (user.getStatus() == 0) {
            // 账号封禁：仍执行密码校验以保持时序一致
            BCrypt.checkpw(StringUtils.hasText(password) ? password : "invalid-password", user.getPasswordHash());
            ok = false;
        } else {
            ok = StringUtils.hasText(password) && BCrypt.checkpw(password, user.getPasswordHash());
        }

        if (!ok) {
            // 同时累计账号维度与 IP 维度失败次数
            loginLockoutService.recordFailure(tid, email);
            loginLockoutService.recordIpFailure(clientIp);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 登录成功，清空账号维度失败计数（IP 维度不清空，避免攻击者借合法账号刷掉 IP 计数）
        loginLockoutService.recordSuccess(tid, email);
        return completeLogin(user);
    }

    private UserVO loginByEmailCode(LoginRequest req) {
        long tid = requireTenantId();
        String email = normalizeEmail(req.getEmail());
        // 安全加固：验证码登录与密码登录保持一致的锁定保护（IP 维度 + 账号维度），
        // 避免攻击者绕到验证码登录路径规避 loginByPassword 的暴力破解防护。
        String clientIp = trustedProxyResolver.resolve(httpRequest);
        loginLockoutService.ensureIpNotLocked(clientIp);
        loginLockoutService.ensureNotLocked(tid, email);

        User user = findTenantUser(tid, email);
        boolean ok = user != null && user.getStatus() != 0 && StringUtils.hasText(req.getEmailCode());
        if (ok) {
            try {
                emailVerificationCodeService.verifyAndConsume(tid, email, EmailCodeScene.LOGIN, req.getEmailCode());
            } catch (BusinessException e) {
                ok = false;
            }
        }

        if (!ok) {
            // 统一累计失败计数，并返回与密码登录一致的不可区分错误信息
            loginLockoutService.recordFailure(tid, email);
            loginLockoutService.recordIpFailure(clientIp);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(), "邮箱或验证码错误");
        }

        loginLockoutService.recordSuccess(tid, email);
        return completeLogin(user);
    }

    private UserVO completeLogin(User user) {
        // Sa-Token 登录
        StpUtil.login(user.getId());
        SaSession session = StpUtil.getSession();
        session.set("userId", user.getId());
        session.set("role", user.getRole());
        session.set("tenantId", user.getTenantId());
        session.set("tenantCode", activeTenantCache.getCode(user.getTenantId()));

        // 每日首次登录奖励 1 积分（检查旧登录日期）
        LocalDate today = LocalDate.now();
        boolean firstLoginToday = user.getLastLoginAt() == null
                || user.getLastLoginAt().toLocalDate().isBefore(today);

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        if (firstLoginToday) {
            pointsService.award(user.getId(), 1, "LOGIN", "每日登录");
        }

        log.info("User logged in: id={}", user.getId());

        UserVO vo = toVO(user);
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setTenantId(user.getTenantId());
        vo.setTenantCode(activeTenantCache.getCode(user.getTenantId()));
        return vo;
    }

    public void logout() {
        StpUtil.logout();
    }

    @Transactional
    public void changePassword(Long userId, String oldPwd, String newPwd) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!BCrypt.checkpw(oldPwd, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }
        // 新密码不得与原密码相同，避免"修改"实为无效操作
        if (BCrypt.checkpw(newPwd, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "新密码不能与原密码相同");
        }
        user.setPasswordHash(BCrypt.hashpw(newPwd, BCrypt.gensalt(10)));
        userMapper.updateById(user);
        // 安全加固（缺陷 5）：密码变更后注销该用户的所有活跃 Sa-Token，
        // 防止旧 token 在 7 天总有效期内继续被攻击者持用。
        invalidateAllSessions(userId, "PASSWORD_CHANGE");
    }

    /**
     * 忘记密码：发送邮箱验证码。
     * 无论邮箱是否存在，统一返回 void，避免用户枚举攻击。
     */
    public void forgotPassword(String email) {
        emailVerificationCodeService.sendCode(email, EmailCodeScene.RESET_PASSWORD);
    }

    @Transactional
    public void resetPassword(String email, String emailCode, String newPassword) {
        long tid = requireTenantId();
        String normalizedEmail = normalizeEmail(email);
        User user = findTenantUser(tid, normalizedEmail);
        if (user == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "验证码无效或已过期");
        }
        emailVerificationCodeService.verifyAndConsume(tid, normalizedEmail, EmailCodeScene.RESET_PASSWORD, emailCode);
        user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt(10)));
        user.setResetToken(null);
        user.setResetTokenExpires(null);
        userMapper.updateById(user);
        // 安全加固（缺陷 5）：邮箱验证码重置密码后，注销该用户的所有活跃 Sa-Token，
        // 与 changePassword 行为对齐，避免旧 token 在 reset 后仍可继续访问。
        invalidateAllSessions(user.getId(), "PASSWORD_RESET");
        log.info("Password reset for user {}", user.getId());
    }


    public UserVO getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    /**
     * 查看他人公开资料（最小披露）。
     *
     * <p>安全加固：原 {@code GET /api/v1/users/{id}} 直接返回完整 {@link UserVO}，
     * 其中含 {@code email}/{@code studentNo}/{@code role}/{@code points}/{@code status}
     * 等敏感字段，任何登录用户都可遍历 id 收集同租户全部用户的邮箱与学号（PII 泄露）。
     * 这与缺陷 1.21 引入 {@link PublicUserVO} 的"最小披露"初衷自相矛盾。</p>
     *
     * <p>本方法仅返回 {@link PublicUserVO}（id/nickname/avatarUrl/bio）。
     * "本人详情"仍走 {@link #getById(Long)} 返回完整 {@link UserVO}。</p>
     */
    public PublicUserVO getPublicById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return PublicUserVO.from(user);
    }

    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            user.setNickname(req.getNickname());
        }
        if (req.getAvatarUrl() != null) {
            // 安全加固（缺陷 1.20）：URL 域名白名单校验，防止 javascript:/data: XSS 与 Open Redirect
            assertHostAllowed(req.getAvatarUrl());
            user.setAvatarUrl(req.getAvatarUrl());
        }
        if (req.getProfileCoverUrl() != null) {
            assertHostAllowed(req.getProfileCoverUrl());
            user.setProfileCoverUrl(req.getProfileCoverUrl());
        }
        if (req.getBio() != null) user.setBio(req.getBio());
        if (req.getCollege() != null) user.setCollege(req.getCollege());
        if (req.getMajor() != null) user.setMajor(req.getMajor());
        if (req.getGrade() != null) user.setGrade(req.getGrade());

        userMapper.updateById(user);
        log.info("User profile updated: id={}", userId);
        return toVO(user);
    }

    /**
     * 校验头像 / 封面 URL 域名是否在白名单内（对应 bugfix.md 漏洞 15，T4.6 加固）。
     *
     * <p>语义反转：早期实现"{@code allowedAssetHosts} 为空 → 全放行"，
     * 让运维忘配前缀的开发环境直接对外暴露 Open Redirect / SSRF 攻击面。
     * T4.6 改为"{@code allowedAssetHosts ∪ selfHosts} 为空 → 直接抛错"，
     * 提示运维必须显式配置允许域名。</p>
     *
     * <p>名单合并策略（与 design.md 主题 4 对齐）：</p>
     * <ul>
     *   <li>{@link SecurityProperties.Upload#getAllowedAssetHosts()}：运维显式配置的资产 CDN / OSS 域名；</li>
     *   <li>{@link SecurityProperties.Upload#getSelfHosts()}：从 storage 端点推导出的本站存储域名，
     *       让 MinIO 自身颁发的 presigned URL 永远在白名单内，无需运维额外维护。</li>
     * </ul>
     *
     * <p>三类放行场景：</p>
     * <ol>
     *   <li>{@code url == null || url.isBlank()}：视为"清空"操作，保留现状；</li>
     *   <li>站内相对路径（{@code uri.getHost() == null} 且以 {@code /} 开头）：
     *       例如 {@code LocalStorageService#issuePublicGetUrl} 返回的
     *       {@code /api/v1/local-storage/<key>}，本站资源天然可信；</li>
     *   <li>{@code host} 命中合并白名单（大小写无关，且 {@code selfHosts} 元素允许是
     *       完整 URL 形式如 {@code http://192.168.x.x:9000}）。</li>
     * </ol>
     *
     * <p>{@code selfHosts} 元素之所以可能是完整 URL，是因为
     * {@code application.yml} 中通过 {@code ${STORAGE_MINIO_ENDPOINT:}} 注入；
     * 若运维直接把 endpoint 字符串作为 self-host 配置，必须支持解析其 host 部分。</p>
     */
    private void assertHostAllowed(String url) {
        if (url == null || url.isBlank()) return;
        // 合并白名单：allowedAssetHosts ∪ selfHosts
        Set<String> allowed = new HashSet<>();
        var assetHosts = securityProperties.getUpload().getAllowedAssetHosts();
        if (assetHosts != null) allowed.addAll(assetHosts);
        var selfHosts = securityProperties.getUpload().getSelfHosts();
        if (selfHosts != null) allowed.addAll(selfHosts);
        // 移除占位符（dev 模式下 STORAGE_MINIO_ENDPOINT 可能为空字符串导致 hosts 集合含 ""）
        allowed.removeIf(h -> h == null || h.isBlank());
        if (allowed.isEmpty()) {
            // 漏洞 15 修复：空白名单语义不再"全放行"，而是"未配置 → 拒绝"
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "未配置允许的资产域名");
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "URL 格式非法");
        }
        String host = uri.getHost();
        if (host == null) {
            // 站内相对路径（如 LocalStorageService 颁发的 /api/v1/local-storage/<key>）放行
            if (url.startsWith("/")) return;
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "URL 解析失败");
        }
        if (!hostMatches(host, allowed)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "URL 域名不在允许列表内：" + host);
        }
    }

    /**
     * 判断实际请求 host 是否匹配白名单中的任一项。
     *
     * <p>白名单元素允许两种形式：</p>
     * <ul>
     *   <li>裸 host：直接做大小写无关的字符串比对；</li>
     *   <li>完整 URL（如 {@code http://192.168.150.130:9000}）：先 {@link URI#getHost()}
     *       提取 host 部分再比对。</li>
     * </ul>
     */
    private static boolean hostMatches(String host, Set<String> allowed) {
        for (String item : allowed) {
            // 优先尝试按"完整 URL"解析
            try {
                URI maybeUri = URI.create(item);
                String allowedHost = maybeUri.getHost();
                if (allowedHost != null && allowedHost.equalsIgnoreCase(host)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // 非合法 URI，按裸 host 走下一个分支
            }
            if (item.equalsIgnoreCase(host)) {
                return true;
            }
        }
        return false;
    }

    @Transactional
    public void banUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该用户已被封禁");
        }
        // 安全加固（缺陷 1.7）：仅允许"调用方权重 ≥ 目标用户权重"，防止 TENANT_ADMIN 封禁 SUPER_ADMIN
        ensureCallerWeightSufficient(user.getRole());
        user.setStatus(0);
        userMapper.updateById(user);
        // 角色被封禁后强制下线，避免被封用户继续持有合法 token 直到过期
        StpUtil.kickout(userId);
        log.info("User banned: id={}", userId);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        ensureCallerWeightSufficient(user.getRole());
        user.setStatus(1);
        userMapper.updateById(user);
        log.info("User unbanned: id={}", userId);
    }

    private static int weightOf(String role) {
        if (role == null) return 0;
        return ROLE_WEIGHT.getOrDefault(role, 0);
    }

    /**
     * 校验调用方权重 ≥ 目标用户当前角色权重（缺陷 1.7）。
     */
    private void ensureCallerWeightSufficient(String targetCurrentRole) {
        String callerRole = (String) StpUtil.getSession().get("role");
        if (weightOf(callerRole) < weightOf(targetCurrentRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "权限不足，无法操作权限更高的用户");
        }
    }

    public List<UserVO> listUsers(String keyword, String role, Integer status, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        if (cursor != null) {
            qw.lt(User::getId, cursor);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(User::getNickname, keyword)
                    .or().like(User::getEmail, keyword)
                    .or().like(User::getStudentNo, keyword));
        }
        if (role != null && !role.isBlank()) {
            qw.eq(User::getRole, role);
        }
        if (status != null) {
            qw.eq(User::getStatus, status);
        }
        qw.orderByDesc(User::getId);
        qw.last("LIMIT " + size);
        return userMapper.selectList(qw).stream().map(this::toVO).toList();
    }

    @Transactional
    public void changeRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!List.of("USER", "TENANT_ADMIN").contains(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的角色");
        }
        String callerRole = (String) StpUtil.getSession().get("role");
        // 安全加固（缺陷 1.7）：调用方权重必须 ≥ 目标用户当前角色权重，且 ≥ 目标新角色权重。
        // 例如 TENANT_ADMIN 不可将 SUPER_ADMIN 改为 USER（反向降级接管）。
        if (weightOf(callerRole) < weightOf(user.getRole())
                || weightOf(callerRole) < weightOf(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "权限不足，无法变更该用户角色");
        }
        // 进一步：提升为 TENANT_ADMIN 需要 SUPER_ADMIN 权限（与历史 Bug fix 1.12 行为一致）
        if ("TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(callerRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "仅超级管理员可提升用户为租户管理员");
        }
        user.setRole(role);
        userMapper.updateById(user);
        // 角色变更后强制下线，确保下次登录时 Sa-Token Session 中的 role 缓存与 DB 一致
        StpUtil.kickout(userId);
        log.info("User role changed: id={}, role={}, by={}", userId, role, callerRole);
    }


    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public Set<String> getMuteSettings(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getMuteSettings() == null) return new HashSet<>();
        try {
            return jsonMapper.readValue(user.getMuteSettings(), new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            return new HashSet<>();
        }
    }

    @Transactional
    public void updateMuteSettings(Long userId, Set<String> muteTypes) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        try {
            user.setMuteSettings(jsonMapper.writeValueAsString(muteTypes));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        userMapper.updateById(user);
    }

    public Set<String> getTagSubscriptions(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getTagSubscriptions() == null) return new HashSet<>();
        try {
            return jsonMapper.readValue(user.getTagSubscriptions(), new TypeReference<Set<String>>() {});
        } catch (JsonProcessingException e) {
            return new HashSet<>();
        }
    }

    @Transactional
    public void updateTagSubscriptions(Long userId, Set<String> tags) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        try {
            user.setTagSubscriptions(jsonMapper.writeValueAsString(tags));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        userMapper.updateById(user);
    }

    /**
     * 查找订阅了指定标签的所有用户 ID。
     * Bug fix 1.15: 使用 SQL 层过滤替代全表加载
     * 安全加固（缺陷 1.4）：
     * <ul>
     *   <li>对 tag 做白名单校验，非法 tag 直接跳过；</li>
     *   <li>对 LIKE 通配符 {@code \\ % _} 进行转义并配合 ESCAPE 子句，防止 tag = "%" 等命中全表；</li>
     *   <li>SQL 显式追加 tenant_id 条件作为深度防御。</li>
     * </ul>
     */
    public Set<Long> findSubscribedUserIds(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Set.of();
        // 白名单 + LIKE 转义
        List<String> safeTags = new ArrayList<>();
        for (String tag : tags) {
            if (tag == null) continue;
            if (!TAG_PATTERN.matcher(tag).matches()) {
                log.debug("Skip invalid tag in findSubscribedUserIds: length={}", tag.length());
                continue;
            }
            String escaped = tag
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");
            safeTags.add(escaped);
        }
        if (safeTags.isEmpty()) return Set.of();

        Long tid = TenantContext.getTenantId();
        if (tid == null) return Set.of();

        // SQL 层粗筛
        List<Long> candidateIds = userMapper.selectUserIdsByTagSubscription(tid, safeTags);
        if (candidateIds.isEmpty()) return Set.of();
        // Java 层精确匹配（SQL LIKE 仍可能有少量误匹配，例如标签包含特殊字符）。
        // 优化：用 selectBatchIds 一次性批量加载候选用户，避免在循环里逐个 selectById 造成的 N+1 查询。
        Set<Long> result = new HashSet<>();
        Set<String> originalTags = new HashSet<>(tags);
        for (User user : userMapper.selectBatchIds(candidateIds)) {
            if (user == null || user.getTagSubscriptions() == null) continue;
            try {
                Set<String> subs = jsonMapper.readValue(user.getTagSubscriptions(),
                        new TypeReference<Set<String>>() {});
                for (String tag : originalTags) {
                    if (subs.contains(tag)) {
                        result.add(user.getId());
                        break;
                    }
                }
            } catch (JsonProcessingException ignored) {}
        }
        return result;
    }

    /**
     * 敏感凭证变更后统一处理：踢下线该用户的全部活跃 token，写入审计日志，
     * 并在监控系统埋点。
     *
     * <p>对应 bugfix.md 漏洞 5（密码变更 / 重置后旧 Sa-Token 仍可用）。
     * 通过 {@link StpUtil#logoutByLoginId(Object)} 让 Redis 中所有该 loginId 的
     * token 立刻失效，与已有的 {@code banUser} / {@code changeRole} 中的
     * {@link StpUtil#kickout(Object)} 行为对齐，使密码 / 重置 / 角色 / 封禁
     * 这四类敏感变更的"踢下线"覆盖一致，不再出现"改密码后旧 token 仍能用 7 天"的会话残留。</p>
     *
     * <p>异常处理策略：</p>
     * <ul>
     *   <li>{@code logoutByLoginId} 失败仅记 WARN 日志，不向上抛出，避免 Sa-Token / Redis
     *       临时不可用时把"修改密码"主业务带挂；</li>
     *   <li>审计日志使用 {@link AuditContext#from(HttpServletRequest, TrustedProxyResolver, Long, Long)}
     *       显式构造上下文，与 {@link AuditLogService#log(AuditContext, String, String, Long, String)}
     *       的 5 参重载配套，避免在异步 / 测试场景下依赖 RequestContextHolder 抛
     *       {@link IllegalStateException}（漏洞 26 已加固）。</li>
     * </ul>
     *
     * @param userId 被强制下线的用户 ID
     * @param action 业务动作标识，作为审计 action 与监控 tag，例如
     *               {@code PASSWORD_CHANGE} / {@code PASSWORD_RESET}
     */
    private void invalidateAllSessions(Long userId, String action) {
        try {
            // 注销该 loginId 在 Sa-Token / Redis 内的所有 token，等同于"踢全部活跃会话"
            // 注：本项目使用 sa-token-spring-boot3-starter，对应 API 为 StpUtil.logout(Object loginId)，
            // 与 design.md 中"logoutByLoginId"的语义等价（按账号 id 注销其所有活跃 token）。
            StpUtil.logout(userId);
        } catch (Exception e) {
            // 仅 WARN，不抛出：避免 Sa-Token / Redis 暂时不可用时影响主业务
            log.warn("StpUtil.logout 失败 userId={}, action={}: {}", userId, action, e.getMessage());
        }
        // 显式快照 AuditContext，避免异步线程下 request-scope 代理抛 IllegalStateException
        AuditContext ctx = AuditContext.from(httpRequest, trustedProxyResolver, userId, TenantContext.getTenantId());
        auditLogService.log(ctx, action, "user", userId, "all sessions invalidated");
        // 监控埋点：按 action tag 分桶累加 session_forced_logout_total
        securityMetrics.sessionForcedLogout(action);
    }

    private User findTenantUser(long tenantId, String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tenantId)
                .eq(User::getEmail, email));
    }

    private long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalStateException("TenantContext is null while handling user operation");
        }
        return tenantId;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private UserVO toVO(User user) {
        return UserVO.builder()
                .id(user.getId())
                .studentNo(user.getStudentNo())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .profileCoverUrl(user.getProfileCoverUrl())
                .bio(user.getBio())
                .college(user.getCollege())
                .major(user.getMajor())
                .grade(user.getGrade())
                .role(user.getRole())
                .points(user.getPoints())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
