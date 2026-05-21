package com.campusforum.post.service;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.achievement.service.AchievementService;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.post.domain.Comment;
import com.campusforum.post.domain.Reaction;
import com.campusforum.post.dto.CommentVO;
import com.campusforum.post.dto.CreateCommentRequest;
import com.campusforum.post.dto.ReactionRequest;
import com.campusforum.post.dto.UpdateCommentRequest;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.post.mapper.ReactionMapper;
import com.campusforum.qa.domain.QaQuestion;
import com.campusforum.qa.mapper.QaQuestionMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.campusforum.notify.websocket.SessionRegistry;
import com.campusforum.sensitive.service.SensitiveWordService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMapper commentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;
    private final AchievementService achievementService;
    private final QaQuestionMapper qaQuestionMapper;
    private final ReactionMapper reactionMapper;
    private final SensitiveWordService sensitiveWordService;
    private final SessionRegistry sessionRegistry;
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Transactional
    public CommentVO create(Long userId, CreateCommentRequest req) {
        // 敏感词过滤：创建前检查
        int riskLevel = sensitiveWordService.getRiskLevel(req.getContent());
        if (riskLevel >= 2) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "评论包含敏感内容，请修改后重试");
        }

        Comment comment = new Comment();
        comment.setPostId(req.getPostId());
        comment.setParentId(req.getParentId());
        comment.setReplyToId(req.getReplyToId());
        comment.setAuthorId(userId);
        comment.setContent(req.getContent());
        comment.setLikeCount(0);
        comment.setStatus(1);

        commentMapper.insert(comment);
        achievementService.onCommentCreated(userId);

        // Bug fix 1.4: 原子更新帖子评论数
        postMapper.incrementCommentCount(req.getPostId(), 1);

        // 通知 + WebSocket 推送评论变更事件
        var post = postMapper.selectById(req.getPostId());
        User commenter = userMapper.selectById(userId);
        String commenterName = commenter != null ? commenter.getNickname() : "有人";

        if (post != null && !post.getAuthorId().equals(userId)) {
            // 评论帖子，通知帖子作者
            notifyService.create(post.getAuthorId(), userId, "COMMENT",
                    "评论通知", commenterName + " 评论了你的帖子", "/posts/" + post.getId());
        }

        if (req.getParentId() != null) {
            // Bug fix 1.18: 显式空值检查
            Comment parentComment = commentMapper.selectById(req.getParentId());
            if (parentComment == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "父评论不存在");
            }
            if (!parentComment.getAuthorId().equals(userId)) {
                notifyService.create(parentComment.getAuthorId(), userId, "REPLY",
                        "回复通知", commenterName + " 回复了你", "/posts/" + req.getPostId());
            }
        }

        // 解析 @提及 并发送通知
        Set<String> mentionedNames = MentionParser.extract(req.getContent());
        for (String name : mentionedNames) {
            User mentioned = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getNickname, name));
            if (mentioned != null && !mentioned.getId().equals(userId)) {
                notifyService.create(mentioned.getId(), userId, "MENTION",
                        "提及通知", commenterName + " @了你", "/posts/" + req.getPostId());
            }
        }

        log.info("Comment created: id={}, postId={}", comment.getId(), req.getPostId());

        // 广播评论变更事件到帖子作者（用于实时刷新评论区）
        if (post != null) {
            broadcastCommentChange(post.getId(), post.getAuthorId(), "NEW_COMMENT");
        }

        return toVO(comment);
    }

    public List<CommentVO> listByPost(Long postId, Long cursor, int limit, boolean qaSort) {
        int size = Math.min(limit, 100);
        LambdaQueryWrapper<Comment> qw = new LambdaQueryWrapper<>();
        qw.eq(Comment::getPostId, postId);
        qw.isNull(Comment::getParentId);
        qw.eq(Comment::getStatus, 1);

        // QA 模式：按分数排序，已采纳答案置顶
        if (qaSort) {
            Long acceptedId = null;
            QaQuestion qa = qaQuestionMapper.selectOne(new LambdaQueryWrapper<QaQuestion>()
                    .eq(QaQuestion::getPostId, postId));
            if (qa != null && qa.getAcceptedCommentId() != null) {
                acceptedId = qa.getAcceptedCommentId();
            }

            // 加载全部顶层评论（QA 帖子回答数有限，全量加载排序）
            List<Comment> allParents = commentMapper.selectList(qw);
            final Long finalAcceptedId = acceptedId;
            allParents.sort((a, b) -> {
                // 已采纳置顶
                boolean aAcc = a.getId().equals(finalAcceptedId);
                boolean bAcc = b.getId().equals(finalAcceptedId);
                if (aAcc && !bAcc) return -1;
                if (!aAcc && bAcc) return 1;
                // 按点赞数降序
                int scoreCmp = Integer.compare(
                        b.getLikeCount() != null ? b.getLikeCount() : 0,
                        a.getLikeCount() != null ? a.getLikeCount() : 0);
                if (scoreCmp != 0) return scoreCmp;
                // 同分按 ID 升序（早回答在前）
                return Long.compare(a.getId(), b.getId());
            });

            List<Comment> parents = allParents;
            if (parents.isEmpty()) return List.of();

            List<Long> parentIds = parents.stream().map(Comment::getId).toList();
            LambdaQueryWrapper<Comment> childQw = new LambdaQueryWrapper<>();
            childQw.in(Comment::getParentId, parentIds);
            childQw.eq(Comment::getStatus, 1);
            childQw.orderByAsc(Comment::getId);
            List<Comment> children = commentMapper.selectList(childQw);

            Map<Long, List<CommentVO>> childMap = children.stream()
                    .map(this::toVO)
                    .collect(Collectors.groupingBy(c -> c.getParentId()));

            return parents.stream().map(p -> {
                CommentVO vo = toVO(p);
                vo.setReplies(childMap.getOrDefault(p.getId(), List.of()));
                return vo;
            }).toList();
        }

        // 普通模式：按 ID 游标分页
        if (cursor != null) {
            qw.gt(Comment::getId, cursor);
        }
        qw.orderByAsc(Comment::getId);
        qw.last("LIMIT " + size);

        List<Comment> parents = commentMapper.selectList(qw);
        if (parents.isEmpty()) return List.of();

        List<Long> parentIds = parents.stream().map(Comment::getId).toList();
        LambdaQueryWrapper<Comment> childQw = new LambdaQueryWrapper<>();
        childQw.in(Comment::getParentId, parentIds);
        childQw.eq(Comment::getStatus, 1);
        childQw.orderByAsc(Comment::getId);
        List<Comment> children = commentMapper.selectList(childQw);

        Map<Long, List<CommentVO>> childMap = children.stream()
                .map(this::toVO)
                .collect(Collectors.groupingBy(c -> c.getParentId()));

        return parents.stream().map(p -> {
            CommentVO vo = toVO(p);
            vo.setReplies(childMap.getOrDefault(p.getId(), List.of()));
            return vo;
        }).toList();
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        String role = (String) StpUtil.getSession().get("role");
        if (!comment.getAuthorId().equals(userId) && !"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        commentMapper.deleteById(commentId);
        log.info("Comment deleted: id={}", commentId);

        // 原子递减帖子评论数
        postMapper.incrementCommentCount(comment.getPostId(), -1);

        // 广播评论变更事件
        broadcastCommentChange(comment.getPostId(), comment.getAuthorId(), "COMMENT_DELETED");
    }

    @Transactional
    public CommentVO updateComment(Long userId, Long commentId, UpdateCommentRequest req) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }
        if (!comment.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "无权编辑此评论");
        }

        // 敏感词过滤
        int riskLevel = sensitiveWordService.getRiskLevel(req.getContent());
        if (riskLevel > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "评论包含敏感内容，请修改后重试");
        }

        comment.setContent(req.getContent());
        comment.setUpdatedAt(LocalDateTime.now());
        commentMapper.updateById(comment);

        log.info("Comment updated: id={}, authorId={}", commentId, userId);
        return toVO(comment);
    }

    @Transactional
    public boolean toggleReaction(Long userId, Long commentId, ReactionRequest req) {
        if (!"LIKE".equals(req.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Comment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getDeleted() == 1 || comment.getStatus() != 1) {
            throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        }

        LambdaQueryWrapper<Reaction> qw = new LambdaQueryWrapper<>();
        qw.eq(Reaction::getUserId, userId)
                .eq(Reaction::getTargetType, "COMMENT")
                .eq(Reaction::getTargetId, commentId)
                .eq(Reaction::getType, req.getType());

        Reaction existing = reactionMapper.selectOne(qw);
        if (existing != null) {
            reactionMapper.deleteById(existing.getId());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            commentMapper.updateById(comment);
            return false;
        }

        Reaction reaction = new Reaction();
        reaction.setUserId(userId);
        reaction.setTargetType("COMMENT");
        reaction.setTargetId(commentId);
        reaction.setType(req.getType());
        reactionMapper.insert(reaction);

        comment.setLikeCount(comment.getLikeCount() + 1);
        commentMapper.updateById(comment);

        User liker = userMapper.selectById(userId);
        String likerName = liker != null ? liker.getNickname() : "有人";
        notifyService.create(comment.getAuthorId(), userId, "LIKE",
                "点赞通知", likerName + " 赞了你的评论", "/posts/" + comment.getPostId());

        return true;
    }

    /**
     * 广播评论变更事件到正在查看该帖子的用户。
     * 通过 WebSocket 推送 COMMENT_CHANGE 事件，前端收到后自动刷新评论列表。
     */
    private void broadcastCommentChange(Long postId, Long postAuthorId, String action) {
        try {
            Map<String, Object> payload = Map.of(
                    "type", "COMMENT_CHANGE",
                    "postId", postId,
                    "action", action
            );
            String json = jsonMapper.writeValueAsString(payload);
            // 通知帖子作者（如果在线）
            sessionRegistry.sendToUser(postAuthorId, json);
        } catch (Exception e) {
            log.debug("Failed to broadcast comment change: {}", e.getMessage());
        }
    }

    private CommentVO toVO(Comment comment) {
        User author = userMapper.selectById(comment.getAuthorId());
        UserVO authorVO = null;
        if (author != null) {
            authorVO = UserVO.builder()
                    .id(author.getId())
                    .nickname(author.getNickname())
                    .avatarUrl(author.getAvatarUrl())
                    .build();
        }

        return CommentVO.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .parentId(comment.getParentId())
                .replyToId(comment.getReplyToId())
                .authorId(comment.getAuthorId())
                .author(authorVO)
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
