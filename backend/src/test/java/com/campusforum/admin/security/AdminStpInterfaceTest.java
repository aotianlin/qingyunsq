package com.campusforum.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class AdminStpInterfaceTest {

    @Autowired
    private AdminStpInterface stpInterface;

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    private Long userId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("admin-test" + ts + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("管理员测试");
        UserVO user = userService.register(req);
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldReturnEmptyPermissionsForRegularUser() {
        List<String> perms = stpInterface.getPermissionList(userId, "login");
        assertThat(perms).isEmpty();
    }

    @Test
    void shouldReturnRoleListForRegularUser() {
        List<String> roles = stpInterface.getRoleList(userId, "login");
        assertThat(roles).contains("USER");
    }

    @Test
    void shouldReturnTenantAdminPermissions() {
        userService.changeRole(userId, "TENANT_ADMIN");
        // 清除可能存在的旧 session 缓存，确保从 DB 读取最新角色
        try { StpUtil.logout(userId); } catch (Exception ignored) {}
        List<String> perms = stpInterface.getPermissionList(userId, "login");
        assertThat(perms).contains(
                "tenant:dashboard",
                "tenant:user:list",
                "tenant:user:ban",
                "tenant:user:role",
                "tenant:post:manage",
                "tenant:space:manage",
                "tenant:audit:log"
        );
    }

    @Test
    void shouldReturnSuperAdminPermissions() {
        // SUPER_ADMIN 只能直接设置数据库（changeRole 不允许）
        User user = userMapper.selectById(userId);
        user.setRole("SUPER_ADMIN");
        userMapper.updateById(user);
        // 清除可能存在的旧 session 缓存
        try { StpUtil.logout(userId); } catch (Exception ignored) {}

        List<String> perms = stpInterface.getPermissionList(userId, "login");
        assertThat(perms).contains(
                "tenant:dashboard",
                "super:tenant:manage"
        );
    }

    @Test
    void shouldReturnTenantAdminRole() {
        userService.changeRole(userId, "TENANT_ADMIN");
        // 清除可能存在的旧 session 缓存，确保从 DB 读取最新角色
        try { StpUtil.logout(userId); } catch (Exception ignored) {}
        List<String> roles = stpInterface.getRoleList(userId, "login");
        assertThat(roles).containsExactly("TENANT_ADMIN");
    }
}
