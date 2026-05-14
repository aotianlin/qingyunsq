package com.campusforum.post.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.achievement.service.AchievementService;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.points.service.PointsService;
import com.campusforum.post.domain.Post;
import com.campusforum.search.service.MeiliSearchClient;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostMapper postMapper;
    private final ReactionMapper reactionMapper;
    private final UserMapper userMapper;
    private final QaQuestionMapper qaQuestionMapper;
    private final NotifyService notifyService;
    private final PointsService pointsService;
    private final AchievementService achievementService;
    private final MeiliSearchClient meiliSearchClient;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PostVO create(Long userId, CreatePostRequest req) {
        Post post = new Post();
        post.setAuthorId(userId);
        post.setScope(req.getScope());
        post.setSpaceId(req.getSpaceId());
        post.setType(req.getType());

        String content = req.getContent();
        if (req.getQuotePostId() != null) {
            Post quoted = postMapper.selectById(req.getQuotePostId());
            if (quoted == null || quoted.getDeleted() == 1) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
            User quotedAuthor = userMapper.selectById(quoted.getAuthorId());
            String quotedName = quotedAuthor != null ? quotedAuthor.getNickname() : "未知用户";
            content = "> **" + quotedName + "** 的原帖：\n> " +
                    (quoted.getTitle() != null ? "**" + quoted.getTitle() + "**\n> " : "") +
                    quoted.getContent().replace("\n", "\n> ") +
                    "\n\n" + (content != null ? content : "");
            post.setType("QUOTE");
        }
        post.setTitle(req.getTitle());
        post.setContent(content);
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
        pointsService.award(userId, 5, "POST", "发表帖子 #" + post.getId());
        achievementService.onPostCreated(userId);
        meiliSearchClient.indexDocument("posts", buildPostDoc(post));
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
        cleanExpiredPins();
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
        meiliSearchClient.deleteDocument("posts", postId);
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

                    // 帖子作者获得 1 积分（不自赞）
                    if (!post.getAuthorId().equals(userId)) {
                        pointsService.award(post.getAuthorId(), 1, "LIKED",
                                "帖子 #" + post.getId() + " 被点赞");
                        achievementService.onPostLiked(post.getAuthorId());
                    }
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

    @Transactional
    public void togglePin(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post != null) {
            boolean wasPinned = post.getIsPinned() == 1;
            post.setIsPinned(wasPinned ? 0 : 1);
            post.setPinnedAt(wasPinned ? null : LocalDateTime.now());
            postMapper.updateById(post);
            meiliSearchClient.indexDocument("posts", buildPostDoc(post));
        }
    }

    @Transactional
    public void toggleEssence(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post != null) {
            post.setIsEssence(post.getIsEssence() == 1 ? 0 : 1);
            postMapper.updateById(post);
            meiliSearchClient.indexDocument("posts", buildPostDoc(post));
        }
    }

    @Transactional
    public void setStatus(Long postId, Integer status) {
        Post post = postMapper.selectById(postId);
        if (post != null) {
            post.setStatus(status);
            postMapper.updateById(post);
            meiliSearchClient.indexDocument("posts", buildPostDoc(post));
        }
    }

    public List<PostVO> pageBySpace(Long spaceId, boolean includeHidden, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getSpaceId, spaceId);
        if (!includeHidden) {
            qw.eq(Post::getStatus, 1);
        }
        if (cursor != null) {
            qw.lt(Post::getId, cursor);
        }
        qw.orderByDesc(Post::getId);
        qw.last("LIMIT " + size);

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return postMapper.selectList(qw).stream().map(p -> toVO(p, currentUserId)).toList();
    }

    public List<PostVO> listPostsForAdmin(String keyword, Integer status, String scope, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        if (cursor != null) {
            qw.lt(Post::getId, cursor);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like(Post::getTitle, keyword)
                    .or().like(Post::getContent, keyword));
        }
        if (status != null) {
            qw.eq(Post::getStatus, status);
        }
        if (scope != null && !scope.isBlank()) {
            qw.eq(Post::getScope, scope);
        }
        qw.orderByDesc(Post::getId);
        qw.last("LIMIT " + size);

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return postMapper.selectList(qw).stream().map(p -> toVO(p, currentUserId)).toList();
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

    private void cleanExpiredPins() {
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getIsPinned, 1);
        qw.isNotNull(Post::getPinnedAt);
        // Unpin posts pinned more than 30 days ago
        qw.lt(Post::getPinnedAt, LocalDateTime.now().minusDays(30));
        List<Post> expired = postMapper.selectList(qw);
        for (Post p : expired) {
            p.setIsPinned(0);
            p.setPinnedAt(null);
            postMapper.updateById(p);
            log.info("Auto-unpinned post {}", p.getId());
        }
    }

    private Map<String, Object> buildPostDoc(Post post) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", post.getId());
        doc.put("title", post.getTitle());
        doc.put("content", post.getContent());
        doc.put("authorId", post.getAuthorId());
        doc.put("createdAt", post.getCreatedAt());
        doc.put("likeCount", post.getLikeCount());
        doc.put("commentCount", post.getCommentCount());
        doc.put("viewCount", post.getViewCount());
        doc.put("status", post.getStatus());
        doc.put("scope", post.getScope());
        doc.put("type", post.getType());
        return doc;
    }
}
