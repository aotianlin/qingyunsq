package com.campusforum.qa.service;

import com.campusforum.common.BusinessException;
import com.campusforum.qa.dto.QaQuestionVO;
import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.service.CommentService;
import com.campusforum.post.service.PostService;
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
import org.springframework.transaction.annotation.Transactional;

import static com.campusforum.test.EmailCodeTestUtils.prepareRegisterCode;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class QaServiceTest {

    @Autowired
    private QaService qaService;

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserService userService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long askerId;
    private Long answererId;
    private Long postId;
    private Long commentId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long ts = System.currentTimeMillis();

        RegisterRequest req1 = new RegisterRequest();
        req1.setEmail("qa-asker-" + ts + "@test.com");
        req1.setPassword("Test123456");
        req1.setNickname("提问者");
        prepareRegisterCode(stringRedisTemplate, req1);
        askerId = userService.register(req1).getId();

        RegisterRequest req2 = new RegisterRequest();
        req2.setEmail("qa-answerer-" + ts + "@test.com");
        req2.setPassword("Test123456");
        req2.setNickname("回答者");
        prepareRegisterCode(stringRedisTemplate, req2);
        answererId = userService.register(req2).getId();

        // 创建 QA 帖子
        CreatePostRequest postReq = new CreatePostRequest();
        postReq.setType("QA");
        postReq.setTitle("Java 怎么学？");
        postReq.setContent("求大佬指点");
        PostVO post = postService.create(askerId, postReq);
        postId = post.getId();

        // 创建回答（评论）
        var commentReq = new com.campusforum.post.dto.CreateCommentRequest();
        commentReq.setPostId(postId);
        commentReq.setContent("先学基础语法，再刷题");
        var commentVO = commentService.create(answererId, commentReq);
        commentId = commentVO.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCreateQaPost() {
        QaQuestionVO qa = qaService.getByPostId(postId);
        assertThat(qa).isNotNull();
        assertThat(qa.getIsSolved()).isFalse();
    }

    @Test
    void shouldAcceptAnswer() {
        QaQuestionVO qa = qaService.accept(postId, commentId, askerId);

        assertThat(qa.getIsSolved()).isTrue();
        assertThat(qa.getAcceptedCommentId()).isEqualTo(commentId);
        assertThat(qa.getSolvedAt()).isNotNull();
    }

    @Test
    void shouldNotAllowDoubleAccept() {
        qaService.accept(postId, commentId, askerId);

        assertThatThrownBy(() -> qaService.accept(postId, commentId, askerId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有采纳");
    }

    @Test
    void shouldNotAllowNonAskerToAccept() {
        assertThatThrownBy(() -> qaService.accept(postId, commentId, answererId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅提问者");
    }
}
