package com.campusforum.space.service;

import com.campusforum.common.BusinessException;
import com.campusforum.space.dto.CreateSpaceRequest;
import com.campusforum.space.dto.SpaceVO;
import com.campusforum.space.dto.UpdateSpaceRequest;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import cn.dev33.satoken.stp.StpUtil;
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
class SpaceServiceTest {

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long ownerId;
    private Long memberId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("space-owner" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("空间创建者");
        prepareRegisterCode(stringRedisTemplate, req);
        UserVO owner = userService.register(req);
        ownerId = owner.getId();

        RegisterRequest req2 = new RegisterRequest();
        req2.setEmail("space-member" + timestamp + "@campusforum.com");
        req2.setPassword("Test123456");
        req2.setNickname("空间成员");
        prepareRegisterCode(stringRedisTemplate, req2);
        UserVO member = userService.register(req2);
        memberId = member.getId();
    }

    @AfterEach
    void tearDown() {
        // 部分用例调用 StpUtil.login 模拟登录态；登出避免污染后续用例的 isLogin 判断
        try {
            StpUtil.logout();
        } catch (Exception ignored) {
            // 无登录态时 logout 可能抛异常，忽略
        }
        TenantContext.clear();
    }

    @Test
    void shouldCreateSpace() {
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("Java 学习小组");
        req.setDescription("一起学 Java");
        req.setCategory("INTEREST");

        SpaceVO space = spaceService.create(ownerId, req);

        assertThat(space.getId()).isNotNull();
        assertThat(space.getName()).isEqualTo("Java 学习小组");
        assertThat(space.getCategory()).isEqualTo("INTEREST");
        assertThat(space.getMemberCount()).isEqualTo(1);
        assertThat(space.getOwner().getId()).isEqualTo(ownerId);
    }

    @Test
    void shouldJoinPublicSpace() {
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("公开空间");
        req.setDescription("测试");
        req.setCategory("CLASS");
        req.setVisibility("PUBLIC");
        SpaceVO space = spaceService.create(ownerId, req);

        SpaceVO joined = spaceService.join(space.getId(), memberId);

        assertThat(joined.getMemberCount()).isEqualTo(2);
        assertThat(joined.getIsMember()).isTrue();
    }

    @Test
    void shouldListSpaces() {
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("列表测试空间");
        req.setDescription("test");
        req.setCategory("MAJOR");
        spaceService.create(ownerId, req);

        List<SpaceVO> spaces = spaceService.list(null, null, 10);

        assertThat(spaces).isNotEmpty();
    }

    @Test
    void shouldNotAllowOwnerToLeave() {
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("不能退的空间");
        req.setDescription("测试");
        req.setCategory("CLUB");
        SpaceVO space = spaceService.create(ownerId, req);

        assertThatThrownBy(() -> spaceService.leave(space.getId(), ownerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("群主不能退出");
    }

    // ========== 回归：非公开空间（REVIEW/INVITE）帖子仅成员可见 ==========

    @Test
    void shouldRejectNonMemberAccessToReviewSpacePosts() {
        // REVIEW（审核制）空间：非成员不应能查看帖子。
        // 历史 bug：checkMemberAccess 误判 "PRIVATE" 导致该拦截失效，非成员可越权读取。
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("审核制空间");
        req.setDescription("测试");
        req.setCategory("CLASS");
        req.setVisibility("REVIEW");
        SpaceVO space = spaceService.create(ownerId, req);

        // memberId 不是该空间成员 → 应被拒绝
        assertThatThrownBy(() -> spaceService.checkMemberAccess(space.getId(), memberId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非空间成员");
    }

    @Test
    void shouldRejectAnonymousAccessToInviteSpacePosts() {
        // INVITE（邀请制）空间：未登录用户（userId=null）不应能查看帖子。
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("邀请制空间");
        req.setDescription("测试");
        req.setCategory("CLUB");
        req.setVisibility("INVITE");
        SpaceVO space = spaceService.create(ownerId, req);

        assertThatThrownBy(() -> spaceService.checkMemberAccess(space.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("需登录");
    }

    @Test
    void shouldAllowMemberAccessToReviewSpacePosts() {
        // REVIEW 空间的 owner 是成员（OWNER），应能正常访问。
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("审核制空间-成员可见");
        req.setDescription("测试");
        req.setCategory("CLASS");
        req.setVisibility("REVIEW");
        SpaceVO space = spaceService.create(ownerId, req);

        // owner 是成员 → 不抛异常
        assertThatCode(() -> spaceService.checkMemberAccess(space.getId(), ownerId))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowAnyoneToAccessPublicSpacePosts() {
        // PUBLIC 空间：非成员、匿名用户都可访问帖子。
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("公开空间-人人可见");
        req.setDescription("测试");
        req.setCategory("MAJOR");
        req.setVisibility("PUBLIC");
        SpaceVO space = spaceService.create(ownerId, req);

        assertThatCode(() -> spaceService.checkMemberAccess(space.getId(), memberId))
                .doesNotThrowAnyException();
        assertThatCode(() -> spaceService.checkMemberAccess(space.getId(), null))
                .doesNotThrowAnyException();
    }

    // ========== 回归：create 取值校验 ==========

    @Test
    void shouldRejectInvalidVisibilityOnCreate() {
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("非法可见性空间");
        req.setCategory("INTEREST");
        req.setVisibility("HACK");

        assertThatThrownBy(() -> spaceService.create(ownerId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可见性");
    }

    @Test
    void shouldRejectInvalidCategoryOnCreate() {
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("非法分类空间");
        req.setCategory("XXX");
        req.setVisibility("PUBLIC");

        assertThatThrownBy(() -> spaceService.create(ownerId, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分类");
    }

    // ========== 回归：sensitiveWords 仅管理员可见 ==========

    @Test
    void shouldHideSensitiveWordsFromNonManagerView() {
        // owner 创建并设置敏感词；列表/非成员视图不应回传 sensitiveWords。
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("带敏感词的公开空间");
        req.setCategory("INTEREST");
        req.setVisibility("PUBLIC");
        SpaceVO created = spaceService.create(ownerId, req);

        UpdateSpaceRequest upd = new UpdateSpaceRequest();
        upd.setSensitiveWords("badword1,badword2");
        spaceService.update(created.getId(), ownerId, upd);

        // list 视图（memberRole=null，非管理员）→ sensitiveWords 应为 null
        List<SpaceVO> spaces = spaceService.list("INTEREST", null, 50);
        SpaceVO inList = spaces.stream()
                .filter(s -> s.getId().equals(created.getId()))
                .findFirst().orElseThrow();
        assertThat(inList.getSensitiveWords()).isNull();
    }

    @Test
    void shouldExposeSensitiveWordsToOwnerView() {
        // owner 查看自己的空间详情（memberRole=OWNER）→ 应能看到 sensitiveWords。
        CreateSpaceRequest req = new CreateSpaceRequest();
        req.setName("owner 可见敏感词空间");
        req.setCategory("INTEREST");
        req.setVisibility("PUBLIC");
        SpaceVO created = spaceService.create(ownerId, req);

        UpdateSpaceRequest upd = new UpdateSpaceRequest();
        upd.setSensitiveWords("secretword");
        spaceService.update(created.getId(), ownerId, upd);

        StpUtil.login(ownerId);
        SpaceVO detail = spaceService.getById(created.getId());
        assertThat(detail.getMemberRole()).isEqualTo("OWNER");
        assertThat(detail.getSensitiveWords()).isEqualTo("secretword");
    }
}
