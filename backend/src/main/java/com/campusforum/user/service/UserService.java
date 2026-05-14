package com.campusforum.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.points.service.PointsService;
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

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PointsService pointsService;
    private final StudentNoMappingProperties studentNoMapping;

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
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, req.getEmail()));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getStatus() == 0) {
            throw new BusinessException(ErrorCode.USER_BANNED);
        }
        if (!BCrypt.checkpw(req.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_PASSWORD);
        }

        // Sa-Token 登录
        StpUtil.login(user.getId());
        StpUtil.getSession().set("userId", user.getId());
        StpUtil.getSession().set("role", user.getRole());

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

    public String forgotPassword(String email) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = base64Encoder.encodeToString(bytes);
        user.setResetToken(token);
        user.setResetTokenExpires(LocalDateTime.now().plusHours(1));
        userMapper.updateById(user);
        log.info("Password reset token generated for user {}, token={}", user.getId(), token);
        return token;
    }

    @Transactional
    public void resetPassword(String email, String token, String newPassword) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getResetToken() == null || !user.getResetToken().equals(token)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "重置令牌无效");
        }
        if (user.getResetTokenExpires() == null || user.getResetTokenExpires().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "重置令牌已过期");
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
        if (req.getAvatarUrl() != null) user.setAvatarUrl(req.getAvatarUrl());
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
     */
    public Set<Long> findSubscribedUserIds(List<String> tags) {
        if (tags == null || tags.isEmpty()) return Set.of();
        Set<Long> result = new HashSet<>();
        List<User> allUsers = userMapper.selectList(new LambdaQueryWrapper<User>()
                .isNotNull(User::getTagSubscriptions)
                .ne(User::getTagSubscriptions, "")
                .ne(User::getTagSubscriptions, "[]"));
        for (User user : allUsers) {
            try {
                Set<String> subs = jsonMapper.readValue(user.getTagSubscriptions(),
                        new TypeReference<Set<String>>() {});
                for (String tag : tags) {
                    if (subs.contains(tag)) {
                        result.add(user.getId());
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
