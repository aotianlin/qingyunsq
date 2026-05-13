package com.campusforum.follow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.follow.domain.Follow;
import com.campusforum.follow.mapper.FollowMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;

    @Transactional
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw new BusinessException(40000, "不能关注自己");
        }
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        qw.eq(Follow::getFollowerId, followerId).eq(Follow::getFolloweeId, followeeId);
        if (followMapper.selectCount(qw) > 0) {
            return; // already following
        }
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        followMapper.insert(follow);
        log.info("Follow: {} -> {}", followerId, followeeId);
    }

    @Transactional
    public void unfollow(Long followerId, Long followeeId) {
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        qw.eq(Follow::getFollowerId, followerId).eq(Follow::getFolloweeId, followeeId);
        followMapper.delete(qw);
        log.info("Unfollow: {} -> {}", followerId, followeeId);
    }

    public boolean isFollowing(Long userId, Long targetId) {
        if (userId == null || targetId == null) return false;
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        qw.eq(Follow::getFollowerId, userId).eq(Follow::getFolloweeId, targetId);
        return followMapper.selectCount(qw) > 0;
    }

    public List<UserVO> getFollowers(Long userId, Long cursor, int limit) {
        return getFollowUsers(userId, "followee", cursor, limit);
    }

    public List<UserVO> getFollowing(Long userId, Long cursor, int limit) {
        return getFollowUsers(userId, "follower", cursor, limit);
    }

    private List<UserVO> getFollowUsers(Long userId, String queryBy, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        if ("followee".equals(queryBy)) {
            qw.eq(Follow::getFolloweeId, userId);
        } else {
            qw.eq(Follow::getFollowerId, userId);
        }
        if (cursor != null) {
            qw.lt(Follow::getId, cursor);
        }
        qw.orderByDesc(Follow::getId);
        qw.last("LIMIT " + size);

        return followMapper.selectList(qw).stream().map(f -> {
            Long uid = "followee".equals(queryBy) ? f.getFollowerId() : f.getFolloweeId();
            User u = userMapper.selectById(uid);
            return u != null ? UserVO.builder()
                    .id(u.getId())
                    .nickname(u.getNickname())
                    .avatarUrl(u.getAvatarUrl())
                    .bio(u.getBio())
                    .build() : null;
        }).filter(v -> v != null).toList();
    }

    public long getFollowerCount(Long userId) {
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        qw.eq(Follow::getFolloweeId, userId);
        return followMapper.selectCount(qw);
    }

    public long getFollowingCount(Long userId) {
        LambdaQueryWrapper<Follow> qw = new LambdaQueryWrapper<>();
        qw.eq(Follow::getFollowerId, userId);
        return followMapper.selectCount(qw);
    }
}
