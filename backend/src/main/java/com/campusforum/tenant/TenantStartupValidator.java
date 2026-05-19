package com.campusforum.tenant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动期租户校验器。
 *
 * <p>standalone 模式：检查 tenants 表存在 (id=standaloneTenantId, status=1)。
 * 若不存在，启动失败并打印明确错误。</p>
 *
 * <p>multi 模式：检查 tenants 表至少有 1 条 status=1 记录，且 root-domain 配置非空。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantStartupValidator implements ApplicationRunner {

    private final TenantProperties props;
    private final TenantMapper tenantMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (props.getMode() == TenantMode.STANDALONE) {
            validateStandaloneMode();
        } else {
            validateMultiMode();
        }
    }

    private void validateStandaloneMode() {
        Tenant t = tenantMapper.selectById(props.getStandaloneTenantId());
        if (t == null || t.getStatus() == null || t.getStatus() != 1) {
            throw new IllegalStateException(
                    "Standalone mode requires tenants table to contain id="
                            + props.getStandaloneTenantId() + " with status=1. "
                            + "Run the bootstrap migration first.");
        }
        log.info("TenantStartupValidator: standalone mode validated, tenantId={}",
                props.getStandaloneTenantId());
    }

    private void validateMultiMode() {
        long activeCount = tenantMapper.selectCount(
                new LambdaQueryWrapper<Tenant>().eq(Tenant::getStatus, 1));
        if (activeCount == 0) {
            throw new IllegalStateException("Multi mode requires at least one active tenant.");
        }
        if (props.getRootDomain() == null || props.getRootDomain().isBlank()) {
            throw new IllegalStateException("Multi mode requires tenant.root-domain to be set.");
        }
        log.info("TenantStartupValidator: multi mode validated, activeCount={}, rootDomain={}",
                activeCount, props.getRootDomain());
    }
}
