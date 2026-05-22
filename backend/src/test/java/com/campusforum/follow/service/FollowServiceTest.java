package com.campusforum.follow.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.follow.domain.Follow;
import com.campusforum.follow.mapper.FollowMapper;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.PublicUserVO;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static com.campusforum.test.EmailCodeTestUtils.prepareRegisterCode;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class FollowServiceTest {

    @Autowired
    private FollowService followService;

    @Autowired
    private FollowMapper followMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long followerId;
    private Long followeeId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();
        RegisterRequest r1 = new RegisterRequest();
        r1.setEmail("follower" + ts + "@campusforum.com");
        r1.setPassword("Test123456");
        r1.setNickname("关注者" + ts);
        prepareRegisterCode(stringRedisTemplate, r1);
        UserVO u1 = userService.register(r1);
        followerId = u1.getId();

        RegisterRequest r2 = new RegisterRequest();
        r2.setEmail("followee" + ts + "@campusforum.com");
        r2.setPassword("Test123456");
        r2.setNickname("被关注者" + ts);
        prepareRegisterCode(stringRedisTemplate, r2);
        UserVO u2 = userService.register(r2);
        followeeId = u2.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldFollow() {
        followService.follow(followerId, followeeId);

        assertThat(followService.isFollowing(followerId, followeeId)).isTrue();
    }

    @Test
    void shouldUnfollow() {
        followService.follow(followerId, followeeId);
        followService.unfollow(followerId, followeeId);

        assertThat(followService.isFollowing(followerId, followeeId)).isFalse();
    }

    @Test
    void shouldPreventSelfFollow() {
        assertThatThrownBy(() -> followService.follow(followerId, followerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能关注自己");
    }

    @Test
    void shouldBeIdempotent() {
        followService.follow(followerId, followeeId);
        followService.follow(followerId, followeeId); // no error on duplicate

        List<Follow> all = followMapper.selectList(
                new QueryWrapper<Follow>().eq("follower_id", followerId));
        assertThat(all).hasSize(1);
    }

    @Test
    void shouldGetFollowers() {
        followService.follow(followerId, followeeId);

        List<PublicUserVO> followers = followService.getFollowers(followeeId, null, 20);
        assertThat(followers).isNotEmpty();
        assertThat(followers.get(0).getId()).isEqualTo(followerId);
    }

    @Test
    void shouldGetFollowing() {
        followService.follow(followerId, followeeId);

        List<PublicUserVO> following = followService.getFollowing(followerId, null, 20);
        assertThat(following).isNotEmpty();
        assertThat(following.get(0).getId()).isEqualTo(followeeId);
    }

    @Test
    void shouldReturnFalseWhenNotFollowing() {
        assertThat(followService.isFollowing(followerId, followeeId)).isFalse();
    }

    @Test
    void shouldGetCounts() {
        followService.follow(followerId, followeeId);

        assertThat(followService.getFollowingCount(followerId)).isEqualTo(1);
        assertThat(followService.getFollowerCount(followeeId)).isEqualTo(1);
        assertThat(followService.getFollowerCount(followerId)).isEqualTo(0);
    }
}
