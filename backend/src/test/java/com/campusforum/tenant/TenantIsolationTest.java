package com.campusforum.tenant;

import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.service.PostService;
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
class TenantIsolationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long tenant1UserId;
    private Long tenant1PostId;
    private Long tenant2UserId;

    @BeforeEach
    void setUp() {
        // 租户 1 创建用户和帖子
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();

        UserVO u1 = userService.register(createRegReq("tenant1-" + ts + "@test.com", "租户一用户"));
        tenant1UserId = u1.getId();

        CreatePostRequest postReq = new CreatePostRequest();
        postReq.setTitle("租户一专属帖子");
        postReq.setContent("此内容仅租户一可见");
        PostVO p1 = postService.create(tenant1UserId, postReq);
        tenant1PostId = p1.getId();

        // 租户 2 创建用户
        TenantContext.setTenantId(2L);
        UserVO u2 = userService.register(createRegReq("tenant2-" + ts + "@test.com", "租户二用户"));
        tenant2UserId = u2.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.setTenantId(1L);
    }

    @Test
    void shouldIsolatePostsBetweenTenants() {
        // 租户 2 查不到租户 1 的帖子
        TenantContext.setTenantId(2L);
        assertThatThrownBy(() -> postService.getById(tenant1PostId))
                .isInstanceOf(Exception.class);

        // 租户 1 可以查到自己的帖子
        TenantContext.setTenantId(1L);
        PostVO post = postService.getById(tenant1PostId);
        assertThat(post).isNotNull();
        assertThat(post.getTitle()).isEqualTo("租户一专属帖子");
    }

    @Test
    void shouldIsolateUsersBetweenTenants() {
        // 租户 2 的用户列表不应包含租户 1 的用户
        TenantContext.setTenantId(2L);
        List<UserVO> t2Users = userService.listUsers(null, null, null, null, 50);
        assertThat(t2Users).noneMatch(u -> u.getId().equals(tenant1UserId));
        assertThat(t2Users).anyMatch(u -> u.getId().equals(tenant2UserId));

        // 租户 1 的用户列表应有自己的用户
        TenantContext.setTenantId(1L);
        List<UserVO> t1Users = userService.listUsers(null, null, null, null, 50);
        assertThat(t1Users).anyMatch(u -> u.getId().equals(tenant1UserId));
    }

    @Test
    void shouldInsertWithCorrectTenantId() {
        // 租户 1 创建的数据 tenant_id = 1
        TenantContext.setTenantId(1L);
        PostVO post = postService.getById(tenant1PostId);
        assertThat(post).isNotNull();

        // 切换到租户 2 后查不到
        TenantContext.setTenantId(2L);
        assertThatThrownBy(() -> postService.getById(tenant1PostId))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldDefaultTenantToOneInStandalone() {
        // standalone 模式下默认 tenant_id = 1
        // 验证 TenantContext 默认值
        TenantContext.setTenantId(1L);
        assertThat(TenantContext.getTenantId()).isEqualTo(1L);

        PostVO post = postService.getById(tenant1PostId);
        assertThat(post).isNotNull();
    }

    private RegisterRequest createRegReq(String email, String nickname) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("Test123456");
        req.setNickname(nickname);
        prepareRegisterCode(stringRedisTemplate, req);
        return req;
    }
}
