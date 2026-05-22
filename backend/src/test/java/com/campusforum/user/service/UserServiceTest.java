package com.campusforum.user.service;

import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void setUp() {
        // login() 现在从 TenantContext 读取 tenantId，测试前必须设置
        TenantContext.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldRegisterNewUser() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("测试用户");
        prepareRegisterCode(req);

        UserVO user = userService.register(req);

        assertThat(user.getId()).isNotNull();
        assertThat(user.getEmail()).startsWith("test");
        assertThat(user.getNickname()).isEqualTo("测试用户");
    }

    @Test
    void shouldRejectDuplicateEmail() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("重复用户");
        prepareRegisterCode(req);
        userService.register(req);

        assertThatThrownBy(() -> userService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已注册");
    }

    @Test
    void shouldLoginWithCorrectPassword() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("login" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("登录测试");
        prepareRegisterCode(req);
        userService.register(req);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(req.getEmail());
        loginReq.setPassword("Test123456");
        UserVO user = userService.login(loginReq);

        assertThat(user.getEmail()).isEqualTo(req.getEmail());
    }

    @Test
    void shouldLoginWithEmailCode() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("codelogin" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("验证码登录测试");
        prepareRegisterCode(req);
        userService.register(req);

        String code = "246810";
        String key = emailCodeKey("login", req.getEmail());
        stringRedisTemplate.opsForValue().set(key, code, 10, TimeUnit.MINUTES);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(req.getEmail());
        loginReq.setLoginType("CODE");
        loginReq.setEmailCode(code);
        UserVO user = userService.login(loginReq);

        assertThat(user.getEmail()).isEqualTo(req.getEmail());
        assertThat(stringRedisTemplate.opsForValue().get(key)).isNull();
    }

    @Test
    void shouldResetPasswordWithEmailCode() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("reset" + timestamp + "@campusforum.com");
        req.setPassword("OldPass123");
        req.setNickname("验证码重置测试");
        prepareRegisterCode(req);
        userService.register(req);

        String code = "135790";
        stringRedisTemplate.opsForValue().set(
                emailCodeKey("reset_password", req.getEmail()),
                code,
                10,
                TimeUnit.MINUTES);

        userService.resetPassword(req.getEmail(), code, "NewPass123");

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(req.getEmail());
        loginReq.setPassword("NewPass123");
        UserVO user = userService.login(loginReq);
        assertThat(user.getEmail()).isEqualTo(req.getEmail());
    }

    // BREAKING CHANGE: 登录失败统一返回 INVALID_CREDENTIALS，不再区分具体原因
    @Test
    void shouldRejectWrongPassword() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("wrongpwd" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("密码测试");
        prepareRegisterCode(req);
        userService.register(req);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(req.getEmail());
        loginReq.setPassword("WrongPassword");

        assertThatThrownBy(() -> userService.login(loginReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
                    assertThat(bex.getMessage()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getMessage());
                });
    }

    /**
     * BREAKING CHANGE: 所有登录失败场景统一返回 INVALID_CREDENTIALS (40101)。
     * 覆盖 4 种失败情况：
     * 1. 当前租户下用户不存在（邮箱未注册）
     * 2. 密码错误
     * 3. 账号被封禁
     * 4. 跨租户邮箱（邮箱存在于其他租户，但当前租户下不存在）
     */
    @Test
    void shouldReturnInvalidCredentialsForAllLoginFailures() {
        long timestamp = System.currentTimeMillis();

        // --- 准备：在租户 1 下注册一个正常用户 ---
        RegisterRequest regReq = new RegisterRequest();
        regReq.setEmail("allfail" + timestamp + "@campusforum.com");
        regReq.setPassword("Test123456");
        regReq.setNickname("统一错误码测试");
        prepareRegisterCode(regReq);
        UserVO registeredUser = userService.register(regReq);

        // --- 场景 1：当前租户下用户不存在（邮箱未注册） ---
        LoginRequest notExistReq = new LoginRequest();
        notExistReq.setEmail("nonexistent" + timestamp + "@campusforum.com");
        notExistReq.setPassword("AnyPassword123");

        assertThatThrownBy(() -> userService.login(notExistReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
                });

        // --- 场景 2：密码错误 ---
        LoginRequest wrongPwdReq = new LoginRequest();
        wrongPwdReq.setEmail(regReq.getEmail());
        wrongPwdReq.setPassword("WrongPassword999");

        assertThatThrownBy(() -> userService.login(wrongPwdReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
                });

        // --- 场景 3：账号被封禁 ---
        // 直接更新数据库将用户状态设为 0（封禁）
        User bannedUser = userMapper.selectById(registeredUser.getId());
        bannedUser.setStatus(0);
        userMapper.updateById(bannedUser);

        LoginRequest bannedReq = new LoginRequest();
        bannedReq.setEmail(regReq.getEmail());
        bannedReq.setPassword("Test123456");

        assertThatThrownBy(() -> userService.login(bannedReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
                });

        // 恢复用户状态以便后续测试
        bannedUser.setStatus(1);
        userMapper.updateById(bannedUser);

        // --- 场景 4：跨租户邮箱（邮箱存在于租户 1，但从租户 2 登录） ---
        TenantContext.setTenantId(2L);

        LoginRequest crossTenantReq = new LoginRequest();
        crossTenantReq.setEmail(regReq.getEmail());  // 该邮箱注册在租户 1
        crossTenantReq.setPassword("Test123456");     // 密码正确，但租户不匹配

        assertThatThrownBy(() -> userService.login(crossTenantReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
                });

        // 恢复租户上下文
        TenantContext.setTenantId(1L);
    }

    private String emailCodeKey(String scene, String email) {
        return "email_code:1:" + scene + ":" + email.toLowerCase();
    }

    private void prepareRegisterCode(RegisterRequest req) {
        String code = "123456";
        req.setEmailCode(code);
        stringRedisTemplate.opsForValue().set(
                emailCodeKey("register", req.getEmail()),
                code,
                10,
                TimeUnit.MINUTES);
    }
}
