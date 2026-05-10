package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.admin.dto.DashboardVO;
import com.campusforum.common.R;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.space.mapper.SpaceMapper;
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

    @GetMapping("/dashboard")
    @SaCheckPermission("tenant:dashboard")
    public R<DashboardVO> dashboard() {
        LocalDate today = LocalDate.now();
        return R.ok(DashboardVO.builder()
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
