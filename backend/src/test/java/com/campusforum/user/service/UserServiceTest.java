package com.campusforum.user.service;

import com.campusforum.common.BusinessException;
import com.campusforum.user.dto.LoginRequest;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void shouldRegisterNewUser() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("测试用户");

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
        userService.register(req);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(req.getEmail());
        loginReq.setPassword("Test123456");
        UserVO user = userService.login(loginReq);

        assertThat(user.getEmail()).isEqualTo(req.getEmail());
    }

    @Test
    void shouldRejectWrongPassword() {
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("wrongpwd" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("密码测试");
        userService.register(req);

        LoginRequest loginReq = new LoginRequest();
        loginReq.setEmail(req.getEmail());
        loginReq.setPassword("WrongPassword");

        assertThatThrownBy(() -> userService.login(loginReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("密码错误");
    }
}
