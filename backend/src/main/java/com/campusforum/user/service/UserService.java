package com.campusforum.user.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.common.TokenHasher;
import com.campusforum.infra.email.EmailProperties;
import com.campusforum.infra.email.EmailService;
import com.campusforum.infra.security.LoginLockoutService;
import com.campusforum.points.service.PointsService;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.user.config.StudentNoMappingProperties;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UpdateProfileRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final StudentNoMappingProperties studentNoMapping;
    private final ActiveTenantCache activeTenantCache;
    private final EmailService emailService;
    private final EmailProperties emailProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final LoginLockoutService loginLockoutService;
    private final com.campusforum.infra.security.TrustedProxyResolver trustedProxyResolver;
    private final jakarta.servlet.http.HttpServletRequest httpRequest;
    private final com.campusforum.infra.security.SecurityProperties securityProperties;

    /**
     * 固定 BCrypt hash，仅用于用户不存在时消耗等量 CPU 时间，防止时序攻击。
     * 该值是一个合法的 BCrypt hash（cost=10），不对应任何真实密码。
     */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Transactional
    public UserVO register(RegisterRequest req) {
        // 检查邮箱是否已注册
        if (userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.getEmail())) > 0) {
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
        user.setEmail(req.getEmail());
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

        userMapper.insert(user);
        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());
        return toVO(user);
    }

    public UserVO login(LoginRequest req) {
        long tid = TenantContext.getTenantId();
        // 解析真实 IP（仅在可信代理后相信 X-Forwarded-For）
        String clientIp = trustedProxyResolver.resolve(httpRequest);
        // 安全加固（缺陷 1.15）：先校验 IP 维度锁定，再校验账号维度锁定
        loginLockoutService.ensureIpNotLocked(clientIp);
        loginLockoutService.ensureNotLocked(tid, req.getEmail());

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getTenantId, tid)
                .eq(User::getEmail, req.getEmail()));

        boolean ok;
        if (user == null) {
            // 防时序攻击：用户不存在时仍执行一次 BCrypt 校验，消耗等量 CPU 时间
            BCrypt.checkpw(req.getPassword(), DUMMY_BCRYPT_HASH);
            ok = false;
        } else if (user.getStatus() == 0) {
            // 账号封禁：仍执行密码校验以保持时序一致
            BCrypt.checkpw(req.getPassword(), user.getPasswordHash());
            ok = false;
        } else {
            ok = BCrypt.checkpw(req.getPassword(), user.getPasswordHash());
        }

        if (!ok) {
            // 同时累计账号维度与 IP 维度失败次数
            loginLockoutService.recordFailure(tid, req.getEmail());
            loginLockoutService.recordIpFailure(clientIp);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 登录成功，清空账号维度失败计数（IP 维度不清空，避免攻击者利用一个合法账号刷掉 IP 计数）
        loginLockoutService.recordSuccess(tid, req.getEmail());

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
        user.setPasswordHash(BCrypt.hashpw(newPwd, BCrypt.gensalt(10)));
        userMapper.updateById(user);
    }

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * 忘记密码：生成重置 token，通过邮件发送重置链接。
     * 包含频率限制（每邮箱 15 分钟最多 5 次）和旧令牌失效逻辑。
     * 无论邮箱是否存在，统一返回 void，避免用户枚举攻击。
     */
    public void forgotPassword(String email) {
        // 频率限制检查（无论邮箱是否存在都执行，防止枚举）
        String rateLimitKey = "reset_rate:" + email;
        if (isRateLimited(rateLimitKey)) {
            log.info("Password reset rate limited for email (suppressed)");
            return; // 静默返回，不暴露限流信息
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        // 邮箱不存在时静默返回，不抛异常，防止枚举
        if (user == null) {
            log.info("Password reset requested for non-existent email (suppressed)");
            return;
        }

        // 使旧令牌失效
        user.setResetToken(null);
        user.setResetTokenExpires(null);

        // 生成新令牌：用户拿到的是明文，数据库仅存 SHA-256 哈希
        // 这样数据库泄漏时攻击者也无法借助 hash 直接重置账号。
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String plainToken = base64Encoder.encodeToString(bytes);
        user.setResetToken(TokenHasher.sha256Hex(plainToken));
        user.setResetTokenExpires(LocalDateTime.now().plusMinutes(emailProperties.getResetTokenExpireMinutes()));
        userMapper.updateById(user);

        // 增加频率计数
        incrementRateLimit(rateLimitKey);

        // 邮件发送原始 token；服务端不再保留明文
        emailService.sendResetEmail(email, plainToken);
        log.info("Password reset token generated and email sent for user id={}", user.getId());
    }

    /**
     * 检查是否超过频率限制。
     *
     * <p>安全加固（缺陷 1.14）：Redis 不可用时 fail-closed（保守拒绝），
     * 避免攻击者借 Redis 抖动绕过忘记密码频率限制实施暴力破解 / 邮件轰炸。</p>
     */
    private boolean isRateLimited(String key) {
        try {
            String countStr = stringRedisTemplate.opsForValue().get(key);
            if (countStr != null) {
                int count = Integer.parseInt(countStr);
                return count >= emailProperties.getRateLimitMaxRequests();
            }
            return false;
        } catch (Exception e) {
            // 敏感写路径 fail-closed
            log.error("Redis unavailable for rate limit check, denying request (fail-closed)", e);
            return true;
        }
    }

    /**
     * 增加频率限制计数
     */
    private void incrementRateLimit(String key) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // 首次设置过期时间
                stringRedisTemplate.expire(key, emailProperties.getRateLimitWindowMinutes(), TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for rate limit increment");
        }
    }

    @Transactional
    public void resetPassword(String email, String token, String newPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        // 用户提交的是明文 token，与数据库中存储的 SHA-256 hash 比对
        String hashed = TokenHasher.sha256Hex(token);
        // 统一返回"令牌无效"，不区分"邮箱不存在"和"token 错误"，防止枚举
        if (user == null || user.getResetToken() == null || hashed == null
                || !user.getResetToken().equals(hashed)) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
        if (user.getResetTokenExpires() == null || user.getResetTokenExpires().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.RESET_TOKEN_INVALID);
        }
        user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt(10)));
        user.setResetToken(null);
        user.setResetTokenExpires(null);
        userMapper.updateById(user);
        log.info("Password reset for user {}", user.getId());
    }

    public UserVO getById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Transactional
    public UserVO updateProfile(Long userId, UpdateProfileRequest req) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getAvatarUrl() != null) {
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
     * 校验头像 / 封面 URL 域名是否在白名单内（缺陷 1.20）。
     * 白名单为空时跳过（开发环境便利），生产应通过 ENV 强制配置。
     * 空字符串视为"清空"，直接放行。
     */
    private void assertHostAllowed(String url) {
        if (url == null || url.isBlank()) return;
        var hosts = securityProperties.getUpload().getAllowedAssetHosts();
        if (hosts == null || hosts.isEmpty()) return;
        try {
            String host = java.net.URI.create(url).getHost();
            if (host == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "URL 解析失败");
            }
            if (!hosts.contains(host)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                        "URL 域名不在允许列表内：" + host);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "URL 格式非法");
        }
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
        // 安全加固：仅允许"调用方权重 ≥ 目标用户权重"，防止 TENANT_ADMIN 封禁 SUPER_ADMIN
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

    /** 角色权重映射，用于"调用方角色 ≥ 目标用户当前角色 / 目标新角色"校验。 */
    private static final java.util.Map<String, Integer> ROLE_WEIGHT = java.util.Map.of(
            "USER", 1,
            "TENANT_ADMIN", 2,
            "SUPER_ADMIN", 3
    );

    private static int weightOf(String role) {
        if (role == null) return 0;
        return ROLE_WEIGHT.getOrDefault(role, 0);
    }

    /**
     * 校验调用方权重 ≥ 目标用户当前角色权重。用于 ban/unban/changeRole 等高权限操作，
     * 防止 TENANT_ADMIN 反向降级或封禁 SUPER_ADMIN（参见缺陷 1.7）。
     */
    private void ensureCallerWeightSufficient(String targetCurrentRole) {
        String callerRole = (String) StpUtil.getSession().get("role");
        if (weightOf(callerRole) < weightOf(targetCurrentRole)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "权限不足，无法操作权限更高的用户");
        }
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

    /** 标签合法字符白名单：中英文/数字/下划线/连字符，长度 1-32。 */
    private static final java.util.regex.Pattern TAG_PATTERN =
            java.util.regex.Pattern.compile("^[\\w\\u4e00-\\u9fa5\\-]{1,32}$");

    /**
     * 查找订阅了指定标签的所有用户 ID。
     * Bug fix 1.15: 使用 SQL 层过滤替代全表加载
     * 安全加固：
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

        Long tid = com.campusforum.tenant.TenantContext.getTenantId();
        if (tid == null) return Set.of();

        // SQL 层粗筛
        List<Long> candidateIds = userMapper.selectUserIdsByTagSubscription(tid, safeTags);
        if (candidateIds.isEmpty()) return Set.of();
        // Java 层精确匹配（SQL LIKE 仍可能有少量误匹配，例如标签包含特殊字符）
        Set<Long> result = new HashSet<>();
        Set<String> originalTags = new HashSet<>(tags);
        for (Long uid : candidateIds) {
            User user = userMapper.selectById(uid);
            if (user == null || user.getTagSubscriptions() == null) continue;
            try {
                Set<String> subs = jsonMapper.readValue(user.getTagSubscriptions(),
                        new TypeReference<Set<String>>() {});
                for (String tag : originalTags) {
                    if (subs.contains(tag)) {
                        result.add(uid);
                        break;
                    }
                }
            } catch (JsonProcessingException ignored) {}
        }
        return result;
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
