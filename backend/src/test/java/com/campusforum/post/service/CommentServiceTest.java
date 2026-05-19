package com.campusforum.post.service;

import com.campusforum.post.dto.CreateCommentRequest;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.dto.ReactionRequest;
import com.campusforum.post.dto.CommentVO;
import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.common.BusinessException;
import com.campusforum.tenant.TenantContext;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CommentServiceTest {

    @Autowired
    private PostService postService;

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserService userService;

    private Long authorId;
    private Long commenterId;
    private Long postId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long timestamp = System.currentTimeMillis();

        RegisterRequest authorReq = new RegisterRequest();
        authorReq.setEmail("comment-post-author" + timestamp + "@campusforum.com");
        authorReq.setPassword("Test123456");
        authorReq.setNickname("帖子作者");
        UserVO author = userService.register(authorReq);
        authorId = author.getId();

        RegisterRequest commenterReq = new RegisterRequest();
        commenterReq.setEmail("comment-user" + timestamp + "@campusforum.com");
        commenterReq.setPassword("Test123456");
        commenterReq.setNickname("评论用户");
        UserVO commenter = userService.register(commenterReq);
        commenterId = commenter.getId();

        CreatePostRequest postReq = new CreatePostRequest();
        postReq.setTitle("评论点赞测试");
        postReq.setContent("用于验证评论点赞计数。");
        PostVO post = postService.create(authorId, postReq);
        postId = post.getId();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldToggleCommentLike() {
        CreateCommentRequest commentReq = new CreateCommentRequest();
        commentReq.setPostId(postId);
        commentReq.setContent("这是一条评论");

        CommentVO created = commentService.create(commenterId, commentReq);
        assertThat(created.getLikeCount()).isEqualTo(0);

        ReactionRequest req = new ReactionRequest();
        req.setType("LIKE");
        boolean liked = commentService.toggleReaction(authorId, created.getId(), req);

        assertThat(liked).isTrue();
        assertThat(commentService.listByPost(postId, null, 20, false))
                .first()
                .extracting(CommentVO::getLikeCount)
                .isEqualTo(1);

        boolean unliked = commentService.toggleReaction(authorId, created.getId(), req);
        assertThat(unliked).isFalse();
        assertThat(commentService.listByPost(postId, null, 20, false))
                .first()
                .extracting(CommentVO::getLikeCount)
                .isEqualTo(0);
    }

    @Test
    void shouldRejectUnsupportedCommentReactionType() {
        CreateCommentRequest commentReq = new CreateCommentRequest();
        commentReq.setPostId(postId);
        commentReq.setContent("这是一条评论");

        CommentVO created = commentService.create(commenterId, commentReq);

        ReactionRequest req = new ReactionRequest();
        req.setType("COLLECT");

        assertThatThrownBy(() -> commentService.toggleReaction(authorId, created.getId(), req))
                .isInstanceOf(BusinessException.class);
    }
}
