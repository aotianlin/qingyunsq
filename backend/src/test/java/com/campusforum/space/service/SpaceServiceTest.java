package com.campusforum.space.service;

import com.campusforum.common.BusinessException;
import com.campusforum.space.dto.CreateSpaceRequest;
import com.campusforum.space.dto.SpaceVO;
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
}
