package com.campusforum.post.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.achievement.service.AchievementService;
import com.campusforum.follow.service.FollowService;
import com.campusforum.infra.sanitize.HtmlSanitizerService;
import com.campusforum.infra.security.TrustedProxyResolver;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.points.service.PointsService;
import com.campusforum.post.domain.Post;
import com.campusforum.search.service.MeiliSearchClient;
import com.campusforum.sensitive.service.SensitiveWordService;
import com.campusforum.post.domain.Reaction;
import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.dto.PostPageRequest;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.dto.ReactionRequest;
import com.campusforum.post.dto.UpdatePostRequest;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.post.mapper.ReactionMapper;
import com.campusforum.qa.domain.QaQuestion;
import com.campusforum.qa.mapper.QaQuestionMapper;
import com.campusforum.space.domain.SpaceMember;
import com.campusforum.space.mapper.SpaceMemberMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.PublicUserVO;
import com.campusforum.user.mapper.UserMapper;
import com.campusforum.user.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final SensitiveWordService sensitiveWordService;
    private final FollowService followService;
    private final UserService userService;
    private final SpaceMemberMapper spaceMemberMapper;
    /** 浏览计数去重器（任务 T5.5 / 漏洞 21）：避免单 user/ip 在 30 分钟窗口内反复刷计数。 */
    private final PostViewDeduper postViewDeduper;
    /** 真实 IP 解析器（与限流 / 审计共用），用于浏览计数 IP 维度去重 key。 */
    private final TrustedProxyResolver trustedProxyResolver;
    /** 当前请求上下文（漏洞 21 修复需要客户端 IP 作为去重维度）。 */
    private final HttpServletRequest httpRequest;
    /**
     * HTML 净化服务（任务 T8.3 / 漏洞 18）：写库前剥离 {@code <script>} / 事件处理属性 /
     * {@code javascript:} 协议 URL，避免存储型 XSS。
     */
    private final HtmlSanitizerService htmlSanitizerService;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public PostVO create(Long userId, CreatePostRequest req) {
        // Bug fix 1.1: 校验用户是否为空间成员
        if (req.getSpaceId() != null) {
            SpaceMember member = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                    .eq(SpaceMember::getSpaceId, req.getSpaceId())
                    .eq(SpaceMember::getUserId, userId)
                    .eq(SpaceMember::getStatus, 1));
            if (member == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "非空间成员，无法发帖");
            }
        }

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
            // 漏洞 20 修复：拼接引用块前对 nickname / title / content 做 Markdown 转义，
            // 避免恶意昵称 / 标题（如 "**X**\n# H1\n>"）破出引用块边界，伪造他人发言或
            // 注入伪造的标题 / 列表 / 代码块到当前帖子渲染流。
            String quotedName = MarkdownEscaper.escape(
                    quotedAuthor != null ? quotedAuthor.getNickname() : "未知用户");
            String quotedTitle = MarkdownEscaper.escape(quoted.getTitle());
            String quotedBody = MarkdownEscaper.escape(quoted.getContent()).replace("\n", "\n> ");
            content = "> **" + quotedName + "** 的原帖：\n> "
                    + (quotedTitle != null && !quotedTitle.isEmpty()
                            ? "**" + quotedTitle + "**\n> "
                            : "")
                    + quotedBody
                    + "\n\n" + (content != null ? content : "");
            post.setType("QUOTE");
        }
        post.setTitle(req.getTitle());
        // 漏洞 18 修复：写库前调用 OWASP HTML Sanitizer，移除 <script> 标签 /
        // 事件处理属性（onerror / onclick 等）/ javascript: 协议 URL，
        // 防止存储型 XSS。POST_POLICY 仍保留 Markdown 渲染所需的常见块级 / 行内标签。
        post.setContent(htmlSanitizerService.sanitizePost(content));
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

        // 敏感词过滤：创建后检查风险等级
        String fullContent = (post.getTitle() != null ? post.getTitle() + " " : "") + post.getContent();
        int riskLevel = sensitiveWordService.getRiskLevel(fullContent);
        if (riskLevel > 0) {
            post.setAiRiskLevel(riskLevel);
            if (riskLevel >= 2) {
                post.setStatus(2); // 高风险自动隐藏
            }
            postMapper.updateById(post);
        }

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

        // 解析 @提及 并发送通知
        notifyMentionedUsers(userId, content, "/posts/" + post.getId());

        // QA 帖子：通知标签订阅者
        if ("QA".equals(req.getType()) && req.getTags() != null && !req.getTags().isEmpty()) {
            Set<Long> subscriberIds = userService.findSubscribedUserIds(req.getTags());
            User author = userMapper.selectById(userId);
            String authorName = author != null ? author.getNickname() : "有人";
            for (Long subId : subscriberIds) {
                if (!subId.equals(userId)) {
                    notifyService.create(subId, userId, "TAG_SUBSCRIBE",
                            "标签订阅", authorName + " 发布了你订阅标签的问答",
                            "/posts/" + post.getId());
                }
            }
        }

        return toVO(post, userId);
    }

    @Transactional
    public PostVO updatePost(Long userId, Long postId, UpdatePostRequest req) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        // 验证作者身份
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权编辑此帖子");
        }

        // 更新字段
        if (req.getTitle() != null) post.setTitle(req.getTitle());
        // 漏洞 18 修复：编辑帖子时同样必须经 HTML 净化，避免攻击者通过 PUT 接口绕过创建侧的过滤。
        post.setContent(htmlSanitizerService.sanitizePost(req.getContent()));

        try {
            if (req.getTopics() != null) post.setTopics(objectMapper.writeValueAsString(req.getTopics()));
            if (req.getTags() != null) post.setTags(objectMapper.writeValueAsString(req.getTags()));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }

        if (req.getAttachments() != null) post.setAttachments(req.getAttachments());

        // 敏感词过滤
        String fullContent = (req.getTitle() != null ? req.getTitle() + " " : "") + req.getContent();
        int riskLevel = sensitiveWordService.getRiskLevel(fullContent);
        post.setAiRiskLevel(riskLevel);

        // 高风险自动隐藏
        if (riskLevel >= 2) {
            post.setStatus(2);
        }

        postMapper.updateById(post);

        // 更新搜索索引
        meiliSearchClient.indexDocument("posts", buildPostDoc(post));

        log.info("Post updated: id={}, authorId={}, riskLevel={}", postId, userId, riskLevel);
        return toVO(post, userId);
    }

    public PostVO getById(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null || post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return toVO(post, currentUserId);
    }

    /**
     * 帖子详情读取 + 浏览计数递增（任务 T5.5 / 漏洞 21）。
     *
     * <p>原实现每次 GET 都直接 {@code incrementViewCount}，攻击者刷新即可线性放大
     * {@code view_count}，污染热度排序 / 精华推荐。当前修复语义：</p>
     * <ul>
     *   <li>未登录访客：保留 "不计数" 现状（避免匿名 IP 维度刷数被滥用）；</li>
     *   <li>管理员（TENANT_ADMIN / SUPER_ADMIN）：保留 "不计数" 现状（管理员浏览不应污染业务热度）；</li>
     *   <li>作者本人浏览自己的帖子：不计数（避免作者自己刷数据）；</li>
     *   <li>其他普通已登录用户：交给 {@link PostViewDeduper} 做 30 分钟窗口去重，
     *       仅当 SETNX 成功才递增一次。</li>
     * </ul>
     * IP 维度 key 必须基于 {@link TrustedProxyResolver#resolve(HttpServletRequest)} 解析，
     * 否则攻击者可通过伪造 {@code X-Forwarded-For} 让每次请求落入不同桶绕过去重。</p>
     */
    public PostVO viewPost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null || post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        String role = currentUserId == null ? null : (String) StpUtil.getSession().get("role");
        // 通过统一的 TrustedProxyResolver 解析真实 IP，避免伪造代理头绕过去重
        String ip = trustedProxyResolver.resolve(httpRequest);

        // 仅普通已登录用户、且非作者本人浏览，且通过去重窗口校验，才计入 view_count
        boolean shouldCount = currentUserId != null
                && !"TENANT_ADMIN".equals(role)
                && !"SUPER_ADMIN".equals(role)
                && !post.getAuthorId().equals(currentUserId)
                && postViewDeduper.shouldCount(id, currentUserId, ip);

        if (shouldCount && postMapper.incrementViewCount(id) > 0) {
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
        }
        return toVO(post, currentUserId);
    }

    public List<PostVO> page(PostPageRequest req) {
        int limit = Math.min(req.getLimit(), 50);
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;

        // "follow" sort: only posts from followed users
        if ("follow".equals(req.getSort())) {
            if (currentUserId == null) return List.of();
            List<Long> followingIds = followService.getFollowingIds(currentUserId);
            if (req.getAuthorId() != null) {
                // 个人主页复用关注流排序时，只保留目标作者与当前用户关注列表的交集。
                followingIds = followingIds.stream()
                        .filter(req.getAuthorId()::equals)
                        .toList();
            }
            if (followingIds.isEmpty()) return List.of();

            LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
            qw.eq(Post::getScope, req.getScope());
            qw.eq(Post::getStatus, 1);
            qw.in(Post::getAuthorId, followingIds);
            Long idCursor = req.getCursorId() != null ? req.getCursorId() : req.getCursor();
            if (idCursor != null) {
                qw.lt(Post::getId, idCursor);
            }
            qw.orderByDesc(Post::getId);
            qw.last("LIMIT " + limit);
            List<Post> posts = postMapper.selectList(qw);
            return posts.stream().map(p -> toVO(p, currentUserId)).toList();
        }

        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getScope, req.getScope());
        qw.eq(Post::getStatus, 1);
        if (req.getAuthorId() != null) {
            qw.eq(Post::getAuthorId, req.getAuthorId());
        }

        if ("trending".equals(req.getSort())) {
            if (req.getCursor() != null) {
                if (req.getCursorId() != null) {
                    qw.and(w -> w.lt(Post::getCommentCount, req.getCursor())
                            .or(x -> x.eq(Post::getCommentCount, req.getCursor())
                                    .lt(Post::getId, req.getCursorId())));
                } else {
                    qw.lt(Post::getCommentCount, req.getCursor());
                }
            }
            qw.orderByDesc(Post::getCommentCount, Post::getId);
        } else if ("essence".equals(req.getSort())) {
            // 精华推荐：展示点赞最多的前十个帖子
            qw.orderByDesc(Post::getLikeCount, Post::getId);
            limit = 10;
        } else {
            Long idCursor = req.getCursorId() != null ? req.getCursorId() : req.getCursor();
            if (idCursor != null) {
                qw.lt(Post::getId, idCursor);
            }
            qw.orderByDesc(Post::getId);
        }

        qw.last("LIMIT " + limit);
        List<Post> posts = postMapper.selectList(qw);

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

        // Bug fix 1.2: TENANT_ADMIN 应用层租户校验
        if ("TENANT_ADMIN".equals(role) && !post.getAuthorId().equals(userId)) {
            Long sessionTenantId = (Long) StpUtil.getSession().get("tenantId");
            if (sessionTenantId != null && !sessionTenantId.equals(post.getTenantId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权操作其他租户的帖子");
            }
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

            // Bug fix 1.4: 原子计数器更新
            if ("POST".equals(req.getTargetType()) && "LIKE".equals(req.getType())) {
                postMapper.incrementLikeCount(req.getTargetId(), -1);
            }
            return false;
        } else {
            Reaction reaction = new Reaction();
            reaction.setUserId(userId);
            reaction.setTargetType(req.getTargetType());
            reaction.setTargetId(req.getTargetId());
            reaction.setType(req.getType());
            reactionMapper.insert(reaction);

            // Bug fix 1.4: 原子计数器更新 + Bug fix 1.18: 空值检查
            if ("POST".equals(req.getTargetType()) && "LIKE".equals(req.getType())) {
                postMapper.incrementLikeCount(req.getTargetId(), 1);

                Post post = postMapper.selectById(req.getTargetId());
                if (post == null || post.getDeleted() == 1) {
                    throw new BusinessException(ErrorCode.POST_NOT_FOUND);
                }

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

    // Bug fix 1.7: 校验帖子归属空间后修改状态
    @Transactional
    public void setStatusForSpace(Long postId, Long spaceId, Integer status) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted() == 1 || !spaceId.equals(post.getSpaceId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND.getCode(), "帖子不存在或不属于该空间");
        }
        post.setStatus(status);
        postMapper.updateById(post);
        meiliSearchClient.indexDocument("posts", buildPostDoc(post));
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
        // 安全加固（缺陷 1.21）：公共场景使用 PublicUserVO，不再回传作者邮箱
        PublicUserVO authorVO = PublicUserVO.from(author);

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

    /**
     * 解析内容中的 @mention 并发送通知给被提及用户。
     */
    private void notifyMentionedUsers(Long senderId, String content, String redirectUrl) {
        if (content == null || content.isBlank()) return;
        Set<String> mentionedNames = MentionParser.extract(content);
        if (mentionedNames.isEmpty()) return;
        User sender = userMapper.selectById(senderId);
        String senderName = sender != null ? sender.getNickname() : "有人";

        for (String name : mentionedNames) {
            User mentioned = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, name));
            if (mentioned != null && !mentioned.getId().equals(senderId)) {
                // 避免重复通知（COMMENT/REPLY 通知已发过的情况由 NotifyService 的 sender==receiver 检查处理）
                notifyService.create(mentioned.getId(), senderId, "MENTION",
                        "提及通知", senderName + " @了你", redirectUrl);
            }
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
        doc.put("topics", post.getTopics());
        doc.put("tags", post.getTags());
        return doc;
    }
}
