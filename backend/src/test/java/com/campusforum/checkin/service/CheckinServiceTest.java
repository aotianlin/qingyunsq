package com.campusforum.checkin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.checkin.dto.*;
import com.campusforum.checkin.mapper.CheckinChallengeMapper;
import com.campusforum.checkin.mapper.CheckinRecordMapper;
import com.campusforum.common.BusinessException;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class CheckinServiceTest {

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private UserService userService;

    private Long userId1;
    private Long userId2;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("ck-user1-" + ts + "@test.com");
        req.setPassword("Test123456");
        req.setNickname("打卡用户1");
        userId1 = userService.register(req).getId();

        RegisterRequest req2 = new RegisterRequest();
        req2.setEmail("ck-user2-" + ts + "@test.com");
        req2.setPassword("Test123456");
        req2.setNickname("打卡用户2");
        userId2 = userService.register(req2).getId();
    }

    @Test
    void shouldCreateChallenge() {
        CreateCheckinChallengeRequest req = new CreateCheckinChallengeRequest();
        req.setName("每日背单词");
        req.setDescription("每天背30个单词");
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(30));

        CheckinChallengeVO c = checkinService.create(userId1, req);

        assertThat(c.getId()).isNotNull();
        assertThat(c.getName()).isEqualTo("每日背单词");
        assertThat(c.getMemberCount()).isEqualTo(0);
        assertThat(c.getCreator().getId()).isEqualTo(userId1);
    }

    @Test
    void shouldCheckin() {
        CreateCheckinChallengeRequest req = new CreateCheckinChallengeRequest();
        req.setName("每日运动");
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(14));
        CheckinChallengeVO c = checkinService.create(userId1, req);

        CreateCheckinRecordRequest r = new CreateCheckinRecordRequest();
        r.setContent("跑了5公里");
        CheckinRecordVO record = checkinService.checkin(c.getId(), userId1, r);

        assertThat(record.getId()).isNotNull();
        assertThat(record.getContent()).isEqualTo("跑了5公里");
        assertThat(record.getCheckinDate()).isEqualTo(LocalDate.now());

        // 验证 member_count 已更新
        StpUtil.login(userId1);
        CheckinChallengeVO refreshed = checkinService.getById(c.getId());
        assertThat(refreshed.getMemberCount()).isEqualTo(1);
        assertThat(refreshed.getIsMember()).isTrue();
        StpUtil.logout();
    }

    @Test
    void shouldNotAllowDuplicateCheckin() {
        CreateCheckinChallengeRequest req = new CreateCheckinChallengeRequest();
        req.setName("不重复打卡");
        req.setStartDate(LocalDate.now());
        req.setEndDate(LocalDate.now().plusDays(7));
        CheckinChallengeVO c = checkinService.create(userId1, req);

        CreateCheckinRecordRequest r = new CreateCheckinRecordRequest();
        r.setContent("day 1");
        checkinService.checkin(c.getId(), userId1, r);

        assertThatThrownBy(() -> checkinService.checkin(c.getId(), userId1, r))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("今日已打卡");
    }

    @Test
    void shouldComputeStreak() {
        // 此测试验证 getById 返回的连续天数不为负数/零逻辑
        CreateCheckinChallengeRequest req = new CreateCheckinChallengeRequest();
        req.setName("连续打卡测试");
        req.setStartDate(LocalDate.now().minusDays(30));
        req.setEndDate(LocalDate.now().plusDays(30));
        CheckinChallengeVO c = checkinService.create(userId1, req);

        CreateCheckinRecordRequest r = new CreateCheckinRecordRequest();
        r.setContent("今天打卡");
        checkinService.checkin(c.getId(), userId1, r);

        StpUtil.login(userId1);
        CheckinChallengeVO detail = checkinService.getById(c.getId());
        // 至少今天打卡后应有 1 天
        assertThat(detail.getMyTotalDays()).isEqualTo(1);
        assertThat(detail.getMyConsecutiveDays()).isGreaterThanOrEqualTo(1);
        StpUtil.logout();
    }

    @Test
    void shouldGetLeaderboard() {
        CreateCheckinChallengeRequest req = new CreateCheckinChallengeRequest();
        req.setName("排行榜测试");
        req.setStartDate(LocalDate.now().minusDays(30));
        req.setEndDate(LocalDate.now().plusDays(30));
        CheckinChallengeVO c = checkinService.create(userId1, req);

        // 用户1打卡
        CreateCheckinRecordRequest r = new CreateCheckinRecordRequest();
        r.setContent("user1 打卡");
        checkinService.checkin(c.getId(), userId1, r);

        // 用户2打卡
        r.setContent("user2 打卡");
        checkinService.checkin(c.getId(), userId2, r);

        List<LeaderboardEntry> lb = checkinService.getLeaderboard(c.getId());

        assertThat(lb).isNotEmpty();
        assertThat(lb).hasSize(2);
        // 两人都是 1 天
        assertThat(lb.get(0).getTotalDays()).isEqualTo(1);
    }
}
