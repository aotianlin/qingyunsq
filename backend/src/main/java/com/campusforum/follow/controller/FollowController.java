package com.campusforum.follow.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.follow.service.FollowService;
import com.campusforum.user.dto.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @PostMapping("/{followeeId}")
    public R<Void> follow(@PathVariable Long followeeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        followService.follow(userId, followeeId);
        return R.ok();
    }

    @DeleteMapping("/{followeeId}")
    public R<Void> unfollow(@PathVariable Long followeeId) {
        Long userId = StpUtil.getLoginIdAsLong();
        followService.unfollow(userId, followeeId);
        return R.ok();
    }

    @GetMapping("/check/{targetId}")
    public R<Map<String, Boolean>> isFollowing(@PathVariable Long targetId) {
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        boolean following = followService.isFollowing(userId, targetId);
        return R.ok(Map.of("following", following));
    }

    @GetMapping("/{userId}/followers")
    public R<List<UserVO>> getFollowers(@PathVariable Long userId,
                                         @RequestParam(required = false) Long cursor,
                                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(followService.getFollowers(userId, cursor, limit));
    }

    @GetMapping("/{userId}/following")
    public R<List<UserVO>> getFollowing(@PathVariable Long userId,
                                         @RequestParam(required = false) Long cursor,
                                         @RequestParam(defaultValue = "20") int limit) {
        return R.ok(followService.getFollowing(userId, cursor, limit));
    }

    @GetMapping("/{userId}/counts")
    public R<Map<String, Long>> getCounts(@PathVariable Long userId) {
        return R.ok(Map.of(
                "followers", followService.getFollowerCount(userId),
                "following", followService.getFollowingCount(userId)
        ));
    }
}
