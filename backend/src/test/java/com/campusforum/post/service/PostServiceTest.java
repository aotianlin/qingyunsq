package com.campusforum.post.service;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.post.dto.CreateCommentRequest;
import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.dto.PostPageRequest;
import com.campusforum.post.dto.PostVO;
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
class PostServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long authorId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long timestamp = System.currentTimeMillis();
        RegisterRequest req = new RegisterRequest();
        req.setEmail("post-author" + timestamp + "@campusforum.com");
        req.setPassword("Test123456");
        req.setNickname("帖子作者");
        prepareRegisterCode(stringRedisTemplate, req);
        UserVO user = userService.register(req);
        authorId = user.getId();
    }

    @AfterEach
    void tearDown() {
        try { StpUtil.logout(authorId); } catch (Exception ignored) {}
        TenantContext.clear();
    }

    @Test
    void shouldCreatePost() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("测试帖子标题");
        req.setContent("这是测试帖子的内容，用于验证发帖功能。");

        PostVO post = postService.create(authorId, req);

        assertThat(post.getId()).isNotNull();
        assertThat(post.getTitle()).isEqualTo("测试帖子标题");
        assertThat(post.getContent()).isEqualTo("这是测试帖子的内容，用于验证发帖功能。");
        assertThat(post.getAuthor().getId()).isEqualTo(authorId);
        assertThat(post.getAuthor().getNickname()).isEqualTo("帖子作者");
        assertThat(post.getViewCount()).isEqualTo(0);
        assertThat(post.getLikeCount()).isEqualTo(0);
        assertThat(post.getCommentCount()).isEqualTo(0);
    }

    @Test
    void shouldCreatePostWithoutTitle() {
        CreatePostRequest req = new CreatePostRequest();
        req.setContent("这是一个无标题的帖子");

        PostVO post = postService.create(authorId, req);

        assertThat(post.getId()).isNotNull();
        assertThat(post.getTitle()).isNull();
    }

    @Test
    void shouldGetPostAndIncrementView() {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle("详情测试帖子");
        req.setContent("测试浏览量递增");
        PostVO created = postService.create(authorId, req);

        // 任务 T5.5 / 漏洞 21：作者本人浏览自己的帖子不再计入 view_count，
        // 因此这里另外注册一名读者作为浏览方，才能验证 "首次浏览 +1" 的预期。
        RegisterRequest readerReq = new RegisterRequest();
        readerReq.setEmail("post-reader" + System.currentTimeMillis() + "@campusforum.com");
        readerReq.setPassword("Test123456");
        readerReq.setNickname("帖子读者");
        prepareRegisterCode(stringRedisTemplate, readerReq);
        Long readerId = userService.register(readerReq).getId();

        StpUtil.login(readerId);
        StpUtil.getSession().set("role", "USER");
        PostVO detail = postService.viewPost(created.getId());

        assertThat(detail.getId()).isEqualTo(created.getId());
        assertThat(detail.getViewCount()).isEqualTo(1); // 创建时 0，读者首次查看 +1
        assertThat(detail.getAuthor().getNickname()).isEqualTo("帖子作者");

        try { StpUtil.logout(readerId); } catch (Exception ignored) {}
    }

    @Test
    void shouldPagePostsByLatest() {
        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = new CreatePostRequest();
            req.setTitle("帖子 " + (i + 1));
            req.setContent("内容 " + (i + 1));
            postService.create(authorId, req);
        }

        PostPageRequest pageReq = new PostPageRequest();
        pageReq.setLimit(10);
        List<PostVO> posts = postService.page(pageReq);

        assertThat(posts).isNotEmpty();
        // 按 ID 倒序，最新的在最前面
        assertThat(posts.get(0).getId()).isGreaterThan(posts.get(posts.size() - 1).getId());
    }

    @Test
    void shouldPagePostsByAuthorId() {
        CreatePostRequest ownReq = new CreatePostRequest();
        ownReq.setTitle("目标作者帖子");
        ownReq.setContent("只应该看到这位作者发布的内容");
        PostVO ownPost = postService.create(authorId, ownReq);

        RegisterRequest otherUserReq = new RegisterRequest();
        otherUserReq.setEmail("other-post-author" + System.currentTimeMillis() + "@campusforum.com");
        otherUserReq.setPassword("Test123456");
        otherUserReq.setNickname("其他作者");
        prepareRegisterCode(stringRedisTemplate, otherUserReq);
        Long otherAuthorId = userService.register(otherUserReq).getId();

        CreatePostRequest otherReq = new CreatePostRequest();
        otherReq.setTitle("其他作者帖子");
        otherReq.setContent("这个帖子不能出现在目标作者主页");
        postService.create(otherAuthorId, otherReq);

        PostPageRequest pageReq = new PostPageRequest();
        pageReq.setAuthorId(authorId);
        pageReq.setLimit(20);

        List<PostVO> posts = postService.page(pageReq);

        assertThat(posts)
                .isNotEmpty()
                .allMatch(post -> post.getAuthorId().equals(authorId));
        assertThat(posts).extracting(PostVO::getId).contains(ownPost.getId());
    }

    @Test
    void shouldThrowWhenPostNotFound() {
        assertThatThrownBy(() -> postService.getById(999999L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void shouldPageTrendingPostsWithCursorAndCursorId() {
        createPostWithComments("热门 1", 3);
        createPostWithComments("热门 2", 3);
        createPostWithComments("热门 3", 1);

        PostPageRequest firstPage = new PostPageRequest();
        firstPage.setSort("trending");
        firstPage.setLimit(2);
        List<PostVO> trending = postService.page(firstPage);

        assertThat(trending).hasSizeGreaterThanOrEqualTo(2);
        assertThat(trending.get(0).getCommentCount()).isGreaterThanOrEqualTo(trending.get(1).getCommentCount());

        PostVO last = trending.get(1);
        PostPageRequest secondPage = new PostPageRequest();
        secondPage.setSort("trending");
        secondPage.setLimit(10);
        secondPage.setCursor((long) last.getCommentCount());
        secondPage.setCursorId(last.getId());

        List<PostVO> next = postService.page(secondPage);
        assertThat(next).allMatch(p -> p.getCommentCount() < last.getCommentCount()
                || (p.getCommentCount().equals(last.getCommentCount()) && p.getId() < last.getId()));
    }

    private PostVO createPostWithComments(String title, int comments) {
        CreatePostRequest req = new CreatePostRequest();
        req.setTitle(title);
        req.setContent("内容：" + title);
        PostVO post = postService.create(authorId, req);

        for (int i = 0; i < comments; i++) {
            CreateCommentRequest commentReq = new CreateCommentRequest();
            commentReq.setPostId(post.getId());
            commentReq.setContent("评论 " + i);
            commentService.create(authorId, commentReq);
        }

        return postService.getById(post.getId());
    }
}
