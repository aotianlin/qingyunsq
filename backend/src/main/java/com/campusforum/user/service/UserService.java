package com.campusforum.user.service;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.email.EmailCodeScene;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final StudentNoMappingProperties studentNoMapping;
    private final ActiveTenantCache activeTenantCache;
    private final LoginLockoutService loginLockoutService;
    private final EmailVerificationCodeService emailVerificationCodeService;

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
        emailVerificationCodeService.verifyAndConsume(email, EmailCodeScene.REGISTER, req.getEmailCode());

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

        userMapper.insert(user);
        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());
        return toVO(user);
    }

    public void sendEmailCode(String email, EmailCodeScene scene) {
        emailVerificationCodeService.sendCode(email, scene);
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
        // 登录前先检查是否已被锁定（基于租户和归一化邮箱）。
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
            // 累计失败次数（无论邮箱是否存在），达到阈值会触发锁定
            loginLockoutService.recordFailure(tid, email);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 登录成功，清空失败计数
        loginLockoutService.recordSuccess(tid, email);
        return completeLogin(user);
    }

    private UserVO loginByEmailCode(LoginRequest req) {
        long tid = requireTenantId();
        String email = normalizeEmail(req.getEmail());
        User user = findTenantUser(tid, email);
        if (user == null || user.getStatus() == 0 || !StringUtils.hasText(req.getEmailCode())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(), "邮箱或验证码错误");
        }

        try {
            emailVerificationCodeService.verifyAndConsume(tid, email, EmailCodeScene.LOGIN, req.getEmailCode());
        } catch (BusinessException e) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS.getCode(), "邮箱或验证码错误");
        }

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
        user.setPasswordHash(BCrypt.hashpw(newPwd, BCrypt.gensalt(10)));
        userMapper.updateById(user);
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
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
        if (req.getProfileCoverUrl() != null) user.setProfileCoverUrl(req.getProfileCoverUrl());
        if (req.getBio() != null) user.setBio(req.getBio());
        if (req.getCollege() != null) user.setCollege(req.getCollege());
        if (req.getMajor() != null) user.setMajor(req.getMajor());
        if (req.getGrade() != null) user.setGrade(req.getGrade());

        userMapper.updateById(user);
        log.info("User profile updated: id={}", userId);
        return toVO(user);
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
        user.setStatus(0);
        userMapper.updateById(user);
        log.info("User banned: id={}", userId);
    }

    @Transactional
    public void unbanUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
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

    @Transactional
    public void changeRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (!List.of("USER", "TENANT_ADMIN").contains(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的角色");
        }
        // Bug fix 1.12: 提升为 TENANT_ADMIN 需要 SUPER_ADMIN 权限
        if ("TENANT_ADMIN".equals(role)) {
            String callerRole = (String) StpUtil.getSession().get("role");
            if (!"SUPER_ADMIN".equals(callerRole)) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "仅超级管理员可提升用户为租户管理员");
            }
        }
        user.setRole(role);
        userMapper.updateById(user);
        log.info("User role changed: id={}, role={}", userId, role);
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
     */
    public Set<Long> findSubscribedUserIds(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Set.of();
        // SQL 层粗筛
        List<Long> candidateIds = userMapper.selectUserIdsByTagSubscription(tags);
        if (candidateIds.isEmpty()) return Set.of();
        // Java 层精确匹配（SQL LIKE 可能有误匹配）
        Set<Long> result = new HashSet<>();
        for (Long uid : candidateIds) {
            User user = userMapper.selectById(uid);
            if (user == null || user.getTagSubscriptions() == null) continue;
            try {
                Set<String> subs = jsonMapper.readValue(user.getTagSubscriptions(),
                        new TypeReference<Set<String>>() {});
                for (String tag : tags) {
                    if (subs.contains(tag)) {
                        result.add(uid);
                        break;
                    }
                }
            } catch (JsonProcessingException ignored) {}
        }
        return result;
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
