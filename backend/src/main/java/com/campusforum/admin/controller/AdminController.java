package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.admin.dto.DashboardVO;
import com.campusforum.common.R;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.space.mapper.SpaceMapper;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final PostMapper postMapper;
    private final SpaceMapper spaceMapper;
    private final CommentMapper commentMapper;
    /** 用于把租户 code 注入 dashboard 响应（漏洞 14）。 */
    private final ActiveTenantCache activeTenantCache;

    @GetMapping("/dashboard")
    @SaCheckPermission("tenant:dashboard")
    public R<DashboardVO> dashboard() {
        LocalDate today = LocalDate.now();
        // 漏洞 14 修复：响应携带 tenantId / tenantCode，便于前端在面板顶部展示
        // "当前租户：xxx"，避免 SUPER_ADMIN 跨租户操作时误判数据归属
        Long tid = TenantContext.getTenantId();
        String tenantCode = tid != null ? activeTenantCache.getCode(tid) : null;
        return R.ok(DashboardVO.builder()
                .tenantId(tid)
                .tenantCode(tenantCode)
                .userCount(userMapper.selectCount(null))
                .postCount(postMapper.selectCount(null))
                .spaceCount(spaceMapper.selectCount(null))
                .commentCount(commentMapper.selectCount(null))
                .todayPostCount(postMapper.selectCount(
                        new LambdaQueryWrapper<Post>().ge(Post::getCreatedAt, today.atStartOfDay())))
                .todayUserCount(userMapper.selectCount(
                        new LambdaQueryWrapper<User>().ge(User::getCreatedAt, today.atStartOfDay())))
                .build());
    }
}
