package com.campusforum.post.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bug fix 1.17: 将过期置顶清理从每次 page() 请求移至定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PinCleanupTask {

    private final PostMapper postMapper;

    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点执行
    @Transactional
    public void cleanExpiredPins() {
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getIsPinned, 1);
        qw.isNotNull(Post::getPinnedAt);
        qw.lt(Post::getPinnedAt, LocalDateTime.now().minusDays(30));
        List<Post> expired = postMapper.selectList(qw);
        for (Post p : expired) {
            p.setIsPinned(0);
            p.setPinnedAt(null);
            postMapper.updateById(p);
            log.info("Auto-unpinned post {}", p.getId());
        }
        if (!expired.isEmpty()) {
            log.info("Cleaned {} expired pinned posts", expired.size());
        }
    }
}
