package com.campusforum.admin.security;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminStpInterface implements StpInterface {

    private final UserMapper userMapper;

    private static final List<String> TENANT_ADMIN_PERMISSIONS = List.of(
            "tenant:dashboard",
            "tenant:user:list",
            "tenant:user:ban",
            "tenant:user:role",
            "tenant:post:manage",
            "tenant:space:manage",
            "tenant:audit:log",
            "tenant:report:manage",
            "tenant:sensitive:manage"
    );

    private static final List<String> SUPER_ADMIN_PERMISSIONS;

    static {
        List<String> all = new ArrayList<>(TENANT_ADMIN_PERMISSIONS);
        all.add("super:tenant:manage");
        SUPER_ADMIN_PERMISSIONS = Collections.unmodifiableList(all);
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        String role = getRole(loginId);
        if ("SUPER_ADMIN".equals(role)) return SUPER_ADMIN_PERMISSIONS;
        if ("TENANT_ADMIN".equals(role)) return TENANT_ADMIN_PERMISSIONS;
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String role = getRole(loginId);
        return role != null ? List.of(role) : List.of();
    }

    private String getRole(Object loginId) {
        try {
            SaSession session = StpUtil.getSessionByLoginId(loginId);
            String role = (String) session.get("role");
            if (role != null) return role;
        } catch (Exception ignored) {
        }
        User user = userMapper.selectById(Long.parseLong(loginId.toString()));
        return user != null ? user.getRole() : null;
    }
}
