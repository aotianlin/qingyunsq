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
import org.springframework.data.redis.core.StringRedisTemplate;

import static com.campusforum.test.EmailCodeTestUtils.prepareRegisterCode;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private Long authorId;
    private Long commenterId;
    private Long replierId;
    private Long postId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(1L);
        long timestamp = System.currentTimeMillis();

        RegisterRequest authorReq = new RegisterRequest();
        authorReq.setEmail("comment-post-author" + timestamp + "@campusforum.com");
        authorReq.setPassword("Test123456");
        authorReq.setNickname("帖子作者");
        prepareRegisterCode(stringRedisTemplate, authorReq);
        UserVO author = userService.register(authorReq);
        authorId = author.getId();

        RegisterRequest commenterReq = new RegisterRequest();
        commenterReq.setEmail("comment-user" + timestamp + "@campusforum.com");
        commenterReq.setPassword("Test123456");
        commenterReq.setNickname("评论用户");
        prepareRegisterCode(stringRedisTemplate, commenterReq);
        UserVO commenter = userService.register(commenterReq);
        commenterId = commenter.getId();

        RegisterRequest replierReq = new RegisterRequest();
        replierReq.setEmail("comment-replier" + timestamp + "@campusforum.com");
        replierReq.setPassword("Test123456");
        replierReq.setNickname("回复用户");
        prepareRegisterCode(stringRedisTemplate, replierReq);
        UserVO replier = userService.register(replierReq);
        replierId = replier.getId();

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

    @Test
    void shouldFlattenNestedRepliesUnderRootComment() {
        CreateCommentRequest rootReq = new CreateCommentRequest();
        rootReq.setPostId(postId);
        rootReq.setContent("主评论");
        CommentVO root = commentService.create(commenterId, rootReq);

        CreateCommentRequest firstReplyReq = new CreateCommentRequest();
        firstReplyReq.setPostId(postId);
        firstReplyReq.setParentId(root.getId());
        firstReplyReq.setReplyToId(root.getId());
        firstReplyReq.setContent("@评论用户 第一条回复");
        CommentVO firstReply = commentService.create(replierId, firstReplyReq);

        CreateCommentRequest nestedReplyReq = new CreateCommentRequest();
        nestedReplyReq.setPostId(postId);
        nestedReplyReq.setParentId(firstReply.getId());
        nestedReplyReq.setReplyToId(firstReply.getId());
        nestedReplyReq.setContent("@回复用户 嵌套回复");
        CommentVO nestedReply = commentService.create(authorId, nestedReplyReq);

        CommentVO listedRoot = commentService.listByPost(postId, null, 20, false).get(0);

        assertThat(listedRoot.getId()).isEqualTo(root.getId());
        assertThat(listedRoot.getReplies())
                .extracting(CommentVO::getId)
                .containsExactly(firstReply.getId(), nestedReply.getId());
    }
}
