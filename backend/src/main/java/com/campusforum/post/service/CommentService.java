package com.campusforum.post.service;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.achievement.service.AchievementService;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.post.domain.Comment;
import com.campusforum.post.dto.CommentVO;
import com.campusforum.post.dto.CreateCommentRequest;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.qa.domain.QaQuestion;
import com.campusforum.qa.mapper.QaQuestionMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public CommentVO create(Long userId, CreateCommentRequest req) {
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

        // 更新帖子评论数
        var post = postMapper.selectById(req.getPostId());
        if (post != null) {
            post.setCommentCount(post.getCommentCount() + 1);
            postMapper.updateById(post);
        }

        // 通知
        User commenter = userMapper.selectById(userId);
        String commenterName = commenter != null ? commenter.getNickname() : "有人";

        if (post != null && !post.getAuthorId().equals(userId)) {
            // 评论帖子，通知帖子作者
            notifyService.create(post.getAuthorId(), userId, "COMMENT",
                    "评论通知", commenterName + " 评论了你的帖子", "/posts/" + post.getId());
        }

        if (req.getParentId() != null) {
            // 回复评论，通知父评论作者
            Comment parentComment = commentMapper.selectById(req.getParentId());
            if (parentComment != null && !parentComment.getAuthorId().equals(userId)) {
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
