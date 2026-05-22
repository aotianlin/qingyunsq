package com.campusforum.search.service;

import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.service.PostService;
import com.campusforum.search.dto.SearchResultVO;
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
class SearchServiceTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private PostService postService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long authorId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("search-test" + ts + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("搜索测试用户");
        prepareRegisterCode(stringRedisTemplate, req);
        UserVO user = userService.register(req);
        authorId = user.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldSearchPostsByKeyword() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("红黑树旋转问题求助");
        req.setContent("数据结构作业中遇到红黑树旋转问题，不知道如何处理");
        postService.create(authorId, req);

        List<SearchResultVO> results = searchService.search("红黑树", "POST", null, null, 20);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getType()).isEqualTo("POST");
        assertThat(results.get(0).getTitle()).contains("红黑树");
    }

    @Test
    void shouldSearchUsersByKeyword() {
        List<SearchResultVO> results = searchService.search("搜索测试", "USER", null, null, 20);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getType()).isEqualTo("USER");
        assertThat(results.get(0).getTitle()).isEqualTo("搜索测试用户");
    }

    @Test
    void shouldReturnEmptyForBlankKeyword() {
        List<SearchResultVO> results = searchService.search("   ", null, null, null, 20);
        assertThat(results).isEmpty();
    }

    @Test
    void shouldFilterByType() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("测试帖子");
        req.setContent("测试内容");
        postService.create(authorId, req);

        List<SearchResultVO> results = searchService.search("测试", "POST", null, null, 20);

        assertThat(results).allMatch(r -> "POST".equals(r.getType()));
    }

    @Test
    void shouldSearchAllTypesWithoutTypeFilter() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("独特关键词");
        req.setContent("确保唯一命中");
        postService.create(authorId, req);

        List<SearchResultVO> results = searchService.search("独特关键词", null, null, null, 20);

        assertThat(results).isNotEmpty();
        assertThat(results.get(0).getTitle()).isEqualTo("独特关键词");
    }
}
