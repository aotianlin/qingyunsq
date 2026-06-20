package com.campusforum.admin.export;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.campusforum.admin.mapper.AuditLogMapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.report.mapper.ReportMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 任务 T8.6 / 漏洞 13：导出行数上限测试。
 *
 * <p>纯单元测试：mock 各 Mapper 让其每次返回 1000 条数据，
 * 第 51 批（51_000 行）触发 {@link ErrorCode#BATCH_SIZE_EXCEEDED}。</p>
 */
class ExportRowsLimitTest {

    @Test
    void over50k_throwsBatchExceeded_users() {
        UserMapper userMapper = mock(UserMapper.class);
        // 任意游标查询都返回 1000 条假数据；ID 单调递增以满足游标 GT 条件
        when(userMapper.selectList(any(Wrapper.class)))
                .thenAnswer(inv -> generateUsers(1000));

        ExportService svc = new ExportService(userMapper,
                mock(PostMapper.class), mock(AuditLogMapper.class), mock(ReportMapper.class));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> svc.export("users", "csv", out, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导出行数超过上限")
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(ErrorCode.BATCH_SIZE_EXCEEDED.getCode());
    }

    @Test
    void over50k_throwsBatchExceeded_posts() {
        PostMapper postMapper = mock(PostMapper.class);
        when(postMapper.selectList(any(Wrapper.class)))
                .thenAnswer(inv -> generatePosts(1000));

        ExportService svc = new ExportService(mock(UserMapper.class), postMapper,
                mock(AuditLogMapper.class), mock(ReportMapper.class));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        assertThatThrownBy(() -> svc.export("posts", "csv", out, false))
                .isInstanceOf(BusinessException.class)
                .extracting(t -> ((BusinessException) t).getCode())
                .isEqualTo(ErrorCode.BATCH_SIZE_EXCEEDED.getCode());
    }

    private List<User> generateUsers(int n) {
        List<User> list = new ArrayList<>(n);
        long base = System.nanoTime() & 0x3FFFFFFFL;
        for (int i = 0; i < n; i++) {
            User u = new User();
            u.setId(base + i);
            u.setEmail("u" + i + "@example.com");
            u.setNickname("nick" + i);
            u.setStudentNo("2023" + String.format("%05d", i));
            u.setRole("USER");
            u.setStatus(1);
            u.setCreatedAt(LocalDateTime.now());
            list.add(u);
        }
        return list;
    }

    private List<Post> generatePosts(int n) {
        List<Post> list = new ArrayList<>(n);
        long base = System.nanoTime() & 0x3FFFFFFFL;
        for (int i = 0; i < n; i++) {
            Post p = new Post();
            p.setId(base + i);
            p.setAuthorId(1L);
            p.setScope("public");
            p.setType("normal");
            p.setTitle("title-" + i);
            p.setViewCount(0);
            p.setLikeCount(0);
            p.setCommentCount(0);
            p.setStatus(1);
            p.setCreatedAt(LocalDateTime.now());
            list.add(p);
        }
        return list;
    }
}
