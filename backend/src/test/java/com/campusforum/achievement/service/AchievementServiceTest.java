package com.campusforum.achievement.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusforum.achievement.domain.Achievement;
import com.campusforum.achievement.domain.UserAchievement;
import com.campusforum.achievement.dto.AchievementVO;
import com.campusforum.achievement.mapper.AchievementMapper;
import com.campusforum.achievement.mapper.UserAchievementMapper;
import com.campusforum.tenant.TenantContext;
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
class AchievementServiceTest {

    @Autowired
    private AchievementService achievementService;

    @Autowired
    private AchievementMapper achievementMapper;

    @Autowired
    private UserAchievementMapper userAchievementMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long userId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("achieve-test" + ts + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("成就测试用户");
        prepareRegisterCode(stringRedisTemplate, req);
        UserVO user = userService.register(req);
        userId = user.getId();
        StpUtil.login(userId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private List<UserAchievement> myAwards() {
        return userAchievementMapper.selectList(
                new QueryWrapper<UserAchievement>().eq("user_id", userId));
    }

    @Test
    void shouldSeedAchievements() {
        List<Achievement> all = achievementMapper.selectList(null);
        assertThat(all).isNotEmpty();
        assertThat(all.stream().map(Achievement::getCode)).contains("FIRST_POST", "CHECKIN_7");
    }

    @Test
    void shouldListUserAchievementsWithAwardStatus() {
        List<AchievementVO> list = achievementService.getUserAchievements(userId);
        assertThat(list).isNotEmpty();
        assertThat(list.stream().allMatch(a -> !a.isAwarded())).isTrue();
    }

    @Test
    void shouldAwardAchievement() {
        List<Achievement> all = achievementMapper.selectList(null);
        Achievement first = all.get(0);

        achievementService.award(userId, first.getId());

        List<UserAchievement> ua = myAwards();
        assertThat(ua).hasSize(1);
        assertThat(ua.get(0).getUserId()).isEqualTo(userId);
        assertThat(ua.get(0).getAchievementId()).isEqualTo(first.getId());
    }

    @Test
    void shouldListAchievementsAfterAward() {
        List<Achievement> all = achievementMapper.selectList(null);
        Achievement first = all.get(0);
        achievementService.award(userId, first.getId());

        List<AchievementVO> list = achievementService.getUserAchievements(userId);
        AchievementVO awarded = list.stream().filter(a -> a.getId().equals(first.getId())).findFirst().orElseThrow();
        assertThat(awarded.isAwarded()).isTrue();
    }

    @Test
    void shouldNotDuplicateAward() {
        List<Achievement> all = achievementMapper.selectList(null);
        Achievement first = all.get(0);
        achievementService.award(userId, first.getId());
        achievementService.award(userId, first.getId());

        List<UserAchievement> ua = myAwards();
        assertThat(ua).hasSize(1);
    }
}
