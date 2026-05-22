package com.campusforum.points.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusforum.points.domain.PointsLog;
import com.campusforum.points.dto.PointsLogVO;
import com.campusforum.points.mapper.PointsLogMapper;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.campusforum.test.EmailCodeTestUtils.prepareRegisterCode;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PointsServiceTest {

    @Autowired
    private PointsService pointsService;

    @Autowired
    private PointsLogMapper pointsLogMapper;

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
        req.setEmail("points-test" + ts + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("积分测试用户");
        prepareRegisterCode(stringRedisTemplate, req);
        UserVO user = userService.register(req);
        userId = user.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldAwardPoints() {
        pointsService.award(userId, 10, "TEST", "测试积分");
        long balance = pointsService.getBalance(userId);
        assertThat(balance).isEqualTo(10);
    }

    @Test
    void shouldAwardMultipleEntries() {
        pointsService.award(userId, 5, "POST", "发帖");
        pointsService.award(userId, 3, "CHECKIN", "打卡");
        pointsService.award(userId, 2, "LIKED", "被赞");

        long balance = pointsService.getBalance(userId);
        assertThat(balance).isEqualTo(10);
    }

    @Test
    void shouldSpendPoints() {
        pointsService.award(userId, 20, "INIT", "初始");
        boolean result = pointsService.spend(userId, 8, "BOUNTY", "悬赏");
        assertThat(result).isTrue();

        long balance = pointsService.getBalance(userId);
        assertThat(balance).isEqualTo(12);
    }

    @Test
    void shouldRejectSpendWhenInsufficient() {
        pointsService.award(userId, 5, "INIT", "初始");
        boolean result = pointsService.spend(userId, 10, "BOUNTY", "超额悬赏");
        assertThat(result).isFalse();

        long balance = pointsService.getBalance(userId);
        assertThat(balance).isEqualTo(5);
    }

    @Test
    @Transactional
    void shouldPagePointsLogs() {
        pointsService.award(userId, 3, "CHECKIN", "打卡");
        pointsService.award(userId, 5, "POST", "发帖");
        pointsService.award(userId, 1, "LIKED", "被赞");

        // 直接用 mapper 查询验证
        List<PointsLog> raw = pointsLogMapper.selectList(
                new QueryWrapper<PointsLog>().eq("user_id", userId).orderByDesc("id").last("LIMIT 20"));
        assertThat(raw).hasSize(3);

        List<PointsLogVO> logs = pointsService.page(userId, null, 20);
        assertThat(logs).hasSize(3);
        assertThat(logs.get(0).getType()).isEqualTo("LIKED");

        long total = logs.stream().mapToLong(PointsLogVO::getAmount).sum();
        assertThat(total).isEqualTo(9);
        assertThat(logs.get(0).getBalance()).isEqualTo(9);
    }

    @Test
    void shouldGetZeroForNewUser() {
        long balance = pointsService.getBalance(userId);
        assertThat(balance).isEqualTo(0);
    }

    @Test
    void shouldIgnoreZeroOrNegativeAward() {
        pointsService.award(userId, 0, "ZERO", "无效");
        pointsService.award(userId, -5, "NEG", "负数");
        long balance = pointsService.getBalance(userId);
        assertThat(balance).isEqualTo(0);
    }
}
