package com.campusforum.report.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusforum.report.domain.Report;
import com.campusforum.report.dto.ReportVO;
import com.campusforum.report.mapper.ReportMapper;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private UserService userService;

    private Long reporterId;
    private Long postId = 100L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("report-test" + ts + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("举报测试用户");
        UserVO user = userService.register(req);
        reporterId = user.getId();
        StpUtil.login(reporterId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private List<Report> mine() {
        return reportMapper.selectList(new QueryWrapper<Report>().eq("reporter_id", reporterId));
    }

    @Test
    void shouldCreateReport() {
        reportService.create("POST", postId, "SPAM", "测试举报");
        List<Report> all = mine();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getReporterId()).isEqualTo(reporterId);
        assertThat(all.get(0).getTargetType()).isEqualTo("POST");
        assertThat(all.get(0).getStatus()).isEqualTo(0);
    }

    @Test
    void shouldPageReports() {
        reportService.create("POST", postId, "SPAM", "举报1");
        reportService.create("COMMENT", 50L, "ABUSE", "举报2");
        reportService.create("USER", reporterId, "FAKE", "举报3");

        List<ReportVO> page = reportService.page(null, 20, null, null);
        assertThat(page.size()).isGreaterThanOrEqualTo(3);
        assertThat(page.get(0).getTargetType()).isEqualTo("USER");
    }

    @Test
    void shouldPageReportsFilterByStatus() {
        reportService.create("POST", postId, "SPAM", null);

        List<ReportVO> pending = reportService.page(null, 20, null, 0);
        assertThat(pending.size()).isGreaterThanOrEqualTo(1);

        List<ReportVO> handled = reportService.page(null, 20, null, 1);
        assertThat(handled.stream().filter(r -> r.getReporterId().equals(reporterId)).count()).isEqualTo(0);
    }

    @Test
    void shouldPageReportsFilterByTargetType() {
        reportService.create("POST", postId, "SPAM", null);
        reportService.create("COMMENT", 50L, "ABUSE", null);

        List<ReportVO> posts = reportService.page(null, 20, "POST", null);
        assertThat(posts.size()).isGreaterThanOrEqualTo(1);
        assertThat(posts.get(0).getTargetType()).isEqualTo("POST");
    }

    @Test
    void shouldHandleReport() {
        reportService.create("POST", postId, "SPAM", null);
        Long reportId = mine().get(0).getId();

        reportService.handle(reportId, 1, "已处理");
        Report updated = reportMapper.selectById(reportId);
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getHandleNote()).isEqualTo("已处理");
        assertThat(updated.getHandlerId()).isNotNull();
        assertThat(updated.getHandledAt()).isNotNull();
    }
}
