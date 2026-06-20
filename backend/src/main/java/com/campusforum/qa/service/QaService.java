package com.campusforum.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.achievement.service.AchievementService;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.post.domain.Comment;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.qa.domain.QaQuestion;
import com.campusforum.qa.dto.QaQuestionVO;
import com.campusforum.qa.mapper.QaQuestionMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final QaQuestionMapper qaQuestionMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;
    private final AchievementService achievementService;

    public QaQuestionVO getByPostId(Long postId) {
        QaQuestion qa = qaQuestionMapper.selectOne(new LambdaQueryWrapper<QaQuestion>()
                .eq(QaQuestion::getPostId, postId));
        if (qa == null) return null;
        return toVO(qa);
    }

    @Transactional
    public QaQuestionVO accept(Long postId, Long commentId, Long userId) {
        Post post = postMapper.selectById(postId);
        if (post == null || post.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.POST_NOT_FOUND);
        }
        if (!"QA".equals(post.getType())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该帖子不是问答类型");
        }
        if (!post.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "仅提问者可以采纳答案");
        }

        QaQuestion qa = qaQuestionMapper.selectOne(new LambdaQueryWrapper<QaQuestion>()
                .eq(QaQuestion::getPostId, postId));
        if (qa == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "问答信息不存在");
        }
        if (qa.getIsSolved() == 1) {
            throw new BusinessException(ErrorCode.QUESTION_ALREADY_SOLVED);
        }

        // Bug fix 1.10: 校验评论归属帖子（在设置 acceptedCommentId 之前）
        Comment acceptedComment = commentMapper.selectById(commentId);
        if (acceptedComment == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "评论不存在");
        }
        if (!acceptedComment.getPostId().equals(postId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该评论不属于此帖子");
        }

        qa.setIsSolved(1);
        qa.setAcceptedCommentId(commentId);
        qa.setSolvedAt(LocalDateTime.now());
        qaQuestionMapper.updateById(qa);

        // 通知被采纳的回答者
        if (!acceptedComment.getAuthorId().equals(userId)) {
            achievementService.onAnswerAccepted(acceptedComment.getAuthorId());
            User questioner = userMapper.selectById(userId);
            String questionerName = questioner != null ? questioner.getNickname() : "提问者";
            notifyService.create(acceptedComment.getAuthorId(), userId, "ACCEPT",
                    "采纳通知", questionerName + " 采纳了你的回答", "/posts/" + postId);
        }

        log.info("Answer accepted: postId={}, commentId={}", postId, commentId);
        return toVO(qa);
    }

    private QaQuestionVO toVO(QaQuestion qa) {
        return QaQuestionVO.builder()
                .id(qa.getId())
                .postId(qa.getPostId())
                .isSolved(qa.getIsSolved() == 1)
                .acceptedCommentId(qa.getAcceptedCommentId())
                .solvedAt(qa.getSolvedAt())
                .build();
    }
}
