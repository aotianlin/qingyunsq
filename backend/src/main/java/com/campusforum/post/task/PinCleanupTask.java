package com.campusforum.post.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.TenantMode;
import com.campusforum.tenant.TenantProperties;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TenantMapper tenantMapper;
    private final TenantProperties tenantProperties;
    private final TransactionTemplate transactionTemplate;

    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点执行
    public void cleanExpiredPins() {
        List<Tenant> tenants = loadTargetTenants();
        if (tenants.isEmpty()) {
            log.warn("No active tenant found for expired pin cleanup");
            return;
        }

        int total = 0;
        for (Tenant tenant : tenants) {
            try {
                TenantContext.setTenantId(tenant.getId());
                TenantContext.setTenantCode(tenant.getCode());
                Integer cleaned = transactionTemplate.execute(status -> cleanExpiredPinsForCurrentTenant());
                total += cleaned == null ? 0 : cleaned;
            } catch (Exception e) {
                log.error("Expired pin cleanup failed for tenant id={}, code={}",
                        tenant.getId(), tenant.getCode(), e);
            } finally {
                TenantContext.clear();
            }
        }

        if (total > 0) {
            log.info("Cleaned {} expired pinned posts across {} tenant(s)", total, tenants.size());
        }
    }

    private List<Tenant> loadTargetTenants() {
        if (tenantProperties.getMode() == TenantMode.STANDALONE) {
            Tenant tenant = tenantMapper.selectById(tenantProperties.getStandaloneTenantId());
            if (tenant == null || tenant.getStatus() == null || tenant.getStatus() != 1) {
                return List.of();
            }
            return List.of(tenant);
        }
        return tenantMapper.selectList(new LambdaQueryWrapper<Tenant>()
                .eq(Tenant::getStatus, 1));
    }

    private int cleanExpiredPinsForCurrentTenant() {
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
        return expired.size();
    }
}
