package com.campusforum.post.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.post.domain.Post;
import com.campusforum.post.domain.Reaction;
import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.dto.PostPageRequest;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.dto.ReactionRequest;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.post.mapper.ReactionMapper;
import com.campusforum.qa.domain.QaQuestion;
import com.campusforum.qa.mapper.QaQuestionMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final ReactionMapper reactionMapper;
    private final UserMapper userMapper;
    private final QaQuestionMapper qaQuestionMapper;
    private final NotifyService notifyService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PostVO create(Long userId, CreatePostRequest req) {
        Post post = new Post();
        post.setAuthorId(userId);
        post.setScope(req.getScope());
        post.setSpaceId(req.getSpaceId());
        post.setType(req.getType());
        post.setTitle(req.getTitle());
        post.setContent(req.getContent());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setIsPinned(0);
        post.setIsEssence(0);
        post.setStatus(1);

        try {
            if (req.getTopics() != null) post.setTopics(objectMapper.writeValueAsString(req.getTopics()));
            if (req.getTags() != null) post.setTags(objectMapper.writeValueAsString(req.getTags()));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        postMapper.insert(post);

        // QA 类型帖子：创建问答扩展记录
        if ("QA".equals(req.getType())) {
            QaQuestion qa = new QaQuestion();
            qa.setPostId(post.getId());
            qa.setBountyPoints(req.getBountyPoints() != null ? req.getBountyPoints() : 0);
            qa.setIsSolved(0);
            qaQuestionMapper.insert(qa);
        }

        log.info("Post created: id={}, authorId={}", post.getId(), userId);
        return toVO(post, userId);
    }

    public PostVO getById(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null || post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        // 更新浏览量
        post.setViewCount(post.getViewCount() + 1);
        postMapper.updateById(post);

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return toVO(post, currentUserId);
    }

    public List<PostVO> page(PostPageRequest req) {
        int limit = Math.min(req.getLimit(), 50);
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getScope, req.getScope());
        qw.eq(Post::getStatus, 1);

        if (req.getCursor() != null) {
            if ("trending".equals(req.getSort())) {
                qw.lt(Post::getLikeCount, req.getCursor());
                qw.orderByDesc(Post::getLikeCount, Post::getId);
            } else if ("essence".equals(req.getSort())) {
                qw.eq(Post::getIsEssence, 1);
                qw.lt(Post::getId, req.getCursor());
                qw.orderByDesc(Post::getId);
            } else {
                // latest
                qw.lt(Post::getId, req.getCursor());
                qw.orderByDesc(Post::getId);
            }
        } else {
            if ("trending".equals(req.getSort())) {
                qw.orderByDesc(Post::getLikeCount, Post::getId);
            } else if ("essence".equals(req.getSort())) {
                qw.eq(Post::getIsEssence, 1);
                qw.orderByDesc(Post::getId);
            } else {
                qw.orderByDesc(Post::getId);
            }
        }

        qw.last("LIMIT " + limit);
        List<Post> posts = postMapper.selectList(qw);

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return posts.stream().map(p -> toVO(p, currentUserId)).toList();
    }

    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        String role = (String) StpUtil.getSession().get("role");
        if (!post.getAuthorId().equals(userId) && !"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        postMapper.deleteById(postId);
        log.info("Post deleted: id={}", postId);
    }

    @Transactional
    public boolean toggleReaction(Long userId, ReactionRequest req) {
        LambdaQueryWrapper<Reaction> qw = new LambdaQueryWrapper<>();
        qw.eq(Reaction::getUserId, userId)
          .eq(Reaction::getTargetType, req.getTargetType())
          .eq(Reaction::getTargetId, req.getTargetId())
          .eq(Reaction::getType, req.getType());

        Reaction existing = reactionMapper.selectOne(qw);
        if (existing != null) {
            reactionMapper.deleteById(existing.getId());

            // 更新计数
            if ("POST".equals(req.getTargetType()) && "LIKE".equals(req.getType())) {
                Post post = postMapper.selectById(req.getTargetId());
                if (post != null) {
                    post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
                    postMapper.updateById(post);
                }
            }
            return false;
        } else {
            Reaction reaction = new Reaction();
            reaction.setUserId(userId);
            reaction.setTargetType(req.getTargetType());
            reaction.setTargetId(req.getTargetId());
            reaction.setType(req.getType());
            reactionMapper.insert(reaction);

            // 更新计数
            if ("POST".equals(req.getTargetType()) && "LIKE".equals(req.getType())) {
                Post post = postMapper.selectById(req.getTargetId());
                if (post != null) {
                    post.setLikeCount(post.getLikeCount() + 1);
                    postMapper.updateById(post);

                    // 通知帖子作者（不通知自己）
                    User liker = userMapper.selectById(userId);
                    String likerName = liker != null ? liker.getNickname() : "有人";
                    notifyService.create(post.getAuthorId(), userId, "LIKE",
                            "点赞通知", likerName + " 赞了你的帖子", "/posts/" + post.getId());
                }
            }
            return true;
        }
    }

    private PostVO toVO(Post post, Long currentUserId) {
        User author = userMapper.selectById(post.getAuthorId());
        UserVO authorVO = null;
        if (author != null) {
            authorVO = UserVO.builder()
                    .id(author.getId())
                    .nickname(author.getNickname())
                    .avatarUrl(author.getAvatarUrl())
                    .email(author.getEmail())
                    .build();
        }

        List<String> topicList = Collections.emptyList();
        List<String> tagList = Collections.emptyList();
        try {
            if (post.getTopics() != null) topicList = objectMapper.readValue(post.getTopics(), List.class);
            if (post.getTags() != null) tagList = objectMapper.readValue(post.getTags(), List.class);
        } catch (JsonProcessingException ignored) {
        }

        boolean liked = false;
        boolean collected = false;
        if (currentUserId != null) {
            liked = reactionMapper.selectCount(new LambdaQueryWrapper<Reaction>()
                    .eq(Reaction::getUserId, currentUserId)
                    .eq(Reaction::getTargetType, "POST")
                    .eq(Reaction::getTargetId, post.getId())
                    .eq(Reaction::getType, "LIKE")) > 0;
            collected = reactionMapper.selectCount(new LambdaQueryWrapper<Reaction>()
                    .eq(Reaction::getUserId, currentUserId)
                    .eq(Reaction::getTargetType, "POST")
                    .eq(Reaction::getTargetId, post.getId())
                    .eq(Reaction::getType, "COLLECT")) > 0;
        }

        return PostVO.builder()
                .id(post.getId())
                .authorId(post.getAuthorId())
                .author(authorVO)
                .scope(post.getScope())
                .spaceId(post.getSpaceId())
                .type(post.getType())
                .title(post.getTitle())
                .content(post.getContent())
                .topics(topicList)
                .tags(tagList)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .isPinned(post.getIsPinned())
                .isEssence(post.getIsEssence())
                .status(post.getStatus())
                .liked(liked)
                .collected(collected)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }
}
