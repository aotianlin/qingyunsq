package com.campusforum.security.tenant;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.infra.security.crypto.CryptoService;
import com.campusforum.tenant.cache.ActiveTenantCache;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import com.campusforum.tenant.service.TenantService;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 单元测试：{@link TenantService#toggleStatus(Long)} 状态切换后必须做缓存失效 +
 * 停用时强制踢下线该租户的全部活跃用户。
 *
 * <p>对应任务 T6.4，关联 bugfix.md 漏洞 19（租户停用缓存未失效 + 已停用租户的活跃用户
 * 仍能继续访问）。</p>
 *
 * <p>测试策略：</p>
 * <ul>
 *   <li>不启动 Spring 容器，直接通过构造器装配 {@link TenantService}，
 *       让单元测试聚焦于"状态切换 → evict → kickout"这条业务时序，
 *       不连真实数据库 / Redis；</li>
 *   <li>用 {@code mockStatic(StpUtil.class)} 拦截静态 kickout 调用，
 *       避免触达真实 Sa-Token 上下文；</li>
 *   <li>{@link TenantMapper} / {@link UserMapper} / {@link ActiveTenantCache}
 *       全部 mock，仅断言"是否被以正确参数调用"。</li>
 * </ul>
 *
 * <p>覆盖以下行为（与 design.md 主题 6 → "TenantService.toggleStatus 加 evict + kickout" 对应）：</p>
 * <ol>
 *   <li>{@code disabling_tenant_kicksOutAllActiveUsers}：
 *       status=1 → 0，必须先 evict 缓存，再对 3 个活跃用户全部调用 kickout；</li>
 *   <li>{@code enabling_tenant_doesNotKickout}：
 *       status=0 → 1，仍需 evict 缓存（让下次解析重新加载），但不应踢任何用户下线；</li>
 *   <li>{@code tenantNotFound_throws}：
 *       传入不存在的 tenantId 时抛 {@link BusinessException}，不触碰 evict / kickout；</li>
 *   <li>{@code kickoutFailure_doesNotPropagate}：
 *       某个 user 的 kickout 抛异常时，整体流程仍正常返回，
 *       且其余用户照常被踢下线，避免"踢一半留一半"的不一致状态。</li>
 * </ol>
 */
class TenantToggleStatusKickoutTest {

    /** 被测对象。 */
    private TenantService tenantService;

    /** Mock 协作者。 */
    private TenantMapper tenantMapper;
    private UserMapper userMapper;
    private ActiveTenantCache activeTenantCache;
    private CryptoService cryptoService;

    /** StpUtil 静态 mock 句柄，{@link AfterEach} 中关闭。 */
    private MockedStatic<StpUtil> stpUtilMock;

    /** 测试常量。 */
    private static final Long TENANT_ID = 100L;
    private static final String TENANT_CODE = "campus-a";

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        userMapper = mock(UserMapper.class);
        activeTenantCache = mock(ActiveTenantCache.class);
        cryptoService = mock(CryptoService.class);

        tenantService = new TenantService(
                tenantMapper,
                cryptoService,
                activeTenantCache,
                userMapper,
                // 漏洞 12 修复（T7.3）后 TenantService 多了一个 @Lazy 注入的
                // TenantAwareAiService，仅在 updateAiConfig 路径会触达，本测试
                // 关注 toggleStatus，不会调用它，给 mock 占位即可。
                mock(com.campusforum.ai.service.TenantAwareAiService.class));

        stpUtilMock = mockStatic(StpUtil.class);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    /**
     * 构造一个目标租户实体。
     */
    private Tenant buildTenant(int status) {
        Tenant t = new Tenant();
        t.setId(TENANT_ID);
        t.setCode(TENANT_CODE);
        t.setName("测试租户 A");
        t.setStatus(status);
        return t;
    }

    /**
     * 构造一个用户实体（仅设置 id 字段供 kickout 使用）。
     */
    private User buildUser(long id) {
        User u = new User();
        u.setId(id);
        u.setStatus(1);
        return u;
    }

    @Test
    @DisplayName("disabling_tenant_kicksOutAllActiveUsers: status=1→0 时 evict 缓存 + 全部活跃用户被 kickout")
    @SuppressWarnings("unchecked")
    void disabling_tenant_kicksOutAllActiveUsers() {
        // 准备：当前租户处于启用状态
        Tenant tenant = buildTenant(1);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(tenant);
        when(tenantMapper.updateById(any(Tenant.class))).thenReturn(1);
        // 该租户下有 3 个活跃用户
        List<User> activeUsers = List.of(buildUser(11L), buildUser(22L), buildUser(33L));
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(activeUsers);

        // 执行
        tenantService.toggleStatus(TENANT_ID);

        // 断言 1：DB 状态被切换为 0（停用）
        assertThat(tenant.getStatus()).isEqualTo(0);
        verify(tenantMapper).updateById(tenant);

        // 断言 2：缓存被 evict（id + code 两个维度同时清掉）
        verify(activeTenantCache, times(1)).evict(eq(TENANT_ID), eq(TENANT_CODE));

        // 断言 3：3 个用户全部被 kickout
        stpUtilMock.verify(() -> StpUtil.kickout(11L), times(1));
        stpUtilMock.verify(() -> StpUtil.kickout(22L), times(1));
        stpUtilMock.verify(() -> StpUtil.kickout(33L), times(1));
    }

    @Test
    @DisplayName("enabling_tenant_doesNotKickout: status=0→1 时 evict 缓存但不踢任何用户")
    void enabling_tenant_doesNotKickout() {
        // 准备：当前租户处于停用状态
        Tenant tenant = buildTenant(0);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(tenant);
        when(tenantMapper.updateById(any(Tenant.class))).thenReturn(1);

        // 执行
        tenantService.toggleStatus(TENANT_ID);

        // 断言 1：DB 状态被切换为 1（启用）
        assertThat(tenant.getStatus()).isEqualTo(1);
        verify(tenantMapper).updateById(tenant);

        // 断言 2：缓存仍需 evict（清掉旧的"非活跃"占位，让下次解析重新加载）
        verify(activeTenantCache, times(1)).evict(eq(TENANT_ID), eq(TENANT_CODE));

        // 断言 3：启用场景下不应枚举活跃用户也不应踢任何人下线
        verifyNoInteractions(userMapper);
        stpUtilMock.verify(() -> StpUtil.kickout(any()), never());
    }

    @Test
    @DisplayName("tenantNotFound_throws: 不存在的 tenantId 抛 BusinessException 且不触碰 evict / kickout")
    void tenantNotFound_throws() {
        // 准备：DB 查不到该租户
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(null);

        // 执行 + 断言：抛业务异常
        assertThatThrownBy(() -> tenantService.toggleStatus(TENANT_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("租户不存在");

        // 断言：早期异常不应触碰任何下游协作者
        verifyNoInteractions(activeTenantCache);
        verifyNoInteractions(userMapper);
        stpUtilMock.verify(() -> StpUtil.kickout(any()), never());
    }

    @Test
    @DisplayName("kickoutFailure_doesNotPropagate: 单用户 kickout 抛异常时整体仍成功，其余用户仍被踢下线")
    @SuppressWarnings("unchecked")
    void kickoutFailure_doesNotPropagate() {
        // 准备：启用 → 停用，3 个活跃用户
        Tenant tenant = buildTenant(1);
        when(tenantMapper.selectById(TENANT_ID)).thenReturn(tenant);
        when(tenantMapper.updateById(any(Tenant.class))).thenReturn(1);
        List<User> activeUsers = List.of(buildUser(11L), buildUser(22L), buildUser(33L));
        when(userMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(activeUsers);

        // 中间那个用户 kickout 抛异常（模拟 Sa-Token Redis 抖动）
        stpUtilMock.when(() -> StpUtil.kickout(22L))
                .thenThrow(new RuntimeException("Sa-Token Redis 暂时不可用"));

        // 执行：不应抛异常
        tenantService.toggleStatus(TENANT_ID);

        // 断言 1：DB 状态切换 + 缓存 evict 都正常完成
        assertThat(tenant.getStatus()).isEqualTo(0);
        verify(activeTenantCache, times(1)).evict(eq(TENANT_ID), eq(TENANT_CODE));

        // 断言 2：其余两个用户仍被尝试 kickout（循环不会因为单点失败而中断）
        stpUtilMock.verify(() -> StpUtil.kickout(11L), times(1));
        stpUtilMock.verify(() -> StpUtil.kickout(22L), times(1));
        stpUtilMock.verify(() -> StpUtil.kickout(33L), times(1));
    }
}
