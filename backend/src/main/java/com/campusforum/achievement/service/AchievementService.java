package com.campusforum.achievement.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.campusforum.achievement.domain.Achievement;
import com.campusforum.achievement.domain.UserAchievement;
import com.campusforum.achievement.dto.AchievementVO;
import com.campusforum.achievement.mapper.AchievementMapper;
import com.campusforum.achievement.mapper.UserAchievementMapper;
import com.campusforum.checkin.domain.CheckinRecord;
import com.campusforum.checkin.mapper.CheckinRecordMapper;
import com.campusforum.post.domain.Comment;
import com.campusforum.post.domain.Post;
import com.campusforum.post.domain.Reaction;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.post.mapper.ReactionMapper;
import com.campusforum.qa.domain.QaQuestion;
import com.campusforum.qa.mapper.QaQuestionMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementMapper achievementMapper;
    private final UserAchievementMapper userAchievementMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final ReactionMapper reactionMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final QaQuestionMapper qaQuestionMapper;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    void seedAchievements() {
        if (achievementMapper.selectCount(null) > 0) return;
        List<Achievement> seeds = new ArrayList<>();

        Achievement a = new Achievement();
        a.setCode("FIRST_POST"); a.setName("初来乍到"); a.setDescription("发表第一篇帖子"); a.setRule("{\"type\":\"POST_COUNT\",\"threshold\":1}"); seeds.add(a);

        Achievement b = new Achievement();
        b.setCode("POST_10"); b.setName("笔耕不辍"); b.setDescription("发表 10 篇帖子"); b.setRule("{\"type\":\"POST_COUNT\",\"threshold\":10}"); seeds.add(b);

        Achievement c = new Achievement();
        c.setCode("FIRST_COMMENT"); c.setName("畅所欲言"); c.setDescription("发表第一条评论"); c.setRule("{\"type\":\"COMMENT_COUNT\",\"threshold\":1}"); seeds.add(c);

        Achievement d = new Achievement();
        d.setCode("COMMENT_20"); d.setName("社交达人"); d.setDescription("发表 20 条评论"); d.setRule("{\"type\":\"COMMENT_COUNT\",\"threshold\":20}"); seeds.add(d);

        Achievement e = new Achievement();
        e.setCode("LIKED_10"); e.setName("广受好评"); e.setDescription("收到 10 个赞"); e.setRule("{\"type\":\"LIKED_COUNT\",\"threshold\":10}"); seeds.add(e);

        Achievement f = new Achievement();
        f.setCode("CHECKIN_7"); f.setName("周打卡王"); f.setDescription("连续打卡 7 天"); f.setRule("{\"type\":\"CHECKIN_STREAK\",\"threshold\":7}"); seeds.add(f);

        Achievement g = new Achievement();
        g.setCode("FIRST_ACCEPTED"); g.setName("一鸣惊人"); g.setDescription("首次被采纳回答"); g.setRule("{\"type\":\"ACCEPTED_COUNT\",\"threshold\":1}"); seeds.add(g);

        Achievement h = new Achievement();
        h.setCode("CHECKIN_COUNT_10"); h.setName("自律达人"); h.setDescription("累计打卡 10 次"); h.setRule("{\"type\":\"CHECKIN_COUNT\",\"threshold\":10}"); seeds.add(h);

        for (Achievement seed : seeds) {
            achievementMapper.insert(seed);
        }
        log.info("Seeded {} achievements", seeds.size());
    }

    public List<AchievementVO> getUserAchievements(Long userId) {
        List<Achievement> all = achievementMapper.selectList(null);
        Set<Long> awardedIds = userAchievementMapper.selectList(
                new LambdaQueryWrapper<UserAchievement>().eq(UserAchievement::getUserId, userId))
                .stream().map(UserAchievement::getAchievementId).collect(Collectors.toSet());

        return all.stream().map(a -> AchievementVO.builder()
                .id(a.getId())
                .code(a.getCode())
                .name(a.getName())
                .description(a.getDescription())
                .iconUrl(a.getIconUrl())
                .awarded(awardedIds.contains(a.getId()))
                .build()).toList();
    }

    public void onPostCreated(Long userId) {
        check(userId, "POST_COUNT");
    }

    public void onCommentCreated(Long userId) {
        check(userId, "COMMENT_COUNT");
    }

    public void onPostLiked(Long authorId) {
        check(authorId, "LIKED_COUNT");
    }

    public void onCheckin(Long userId) {
        check(userId, "CHECKIN_COUNT");
        check(userId, "CHECKIN_STREAK");
    }

    public void onAnswerAccepted(Long userId) {
        check(userId, "ACCEPTED_COUNT");
    }

    private void check(Long userId, String triggerType) {
        List<Achievement> relevant = achievementMapper.selectList(
                new QueryWrapper<Achievement>().like("rule", triggerType));

        for (Achievement a : relevant) {
            if (isAwarded(userId, a.getId())) continue;
            long current = countStat(userId, a.getRule());
            int threshold = parseThreshold(a.getRule());
            if (current >= threshold) {
                award(userId, a.getId());
            }
        }
    }

    private long countStat(Long userId, String ruleJson) {
        try {
            JsonNode ruleNode = objectMapper.readTree(ruleJson);
            String type = ruleNode.path("type").asText("");

            if ("POST_COUNT".equals(type)) {
                return postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getAuthorId, userId));
            }
            if ("COMMENT_COUNT".equals(type)) {
                return commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getAuthorId, userId));
            }
            if ("LIKED_COUNT".equals(type)) {
                List<Post> posts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .select(Post::getId).eq(Post::getAuthorId, userId));
                if (posts.isEmpty()) return 0;
                List<Long> postIds = posts.stream().map(Post::getId).toList();
                return reactionMapper.selectCount(new LambdaQueryWrapper<Reaction>()
                        .eq(Reaction::getTargetType, "POST")
                        .eq(Reaction::getType, "LIKE")
                        .in(Reaction::getTargetId, postIds));
            }
            if ("CHECKIN_COUNT".equals(type)) {
                return checkinRecordMapper.selectCount(new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getUserId, userId));
            }
            if ("CHECKIN_STREAK".equals(type)) {
                return computeStreak(userId);
            }
            if ("ACCEPTED_COUNT".equals(type)) {
                List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                        .select(Comment::getId).eq(Comment::getAuthorId, userId));
                if (comments.isEmpty()) return 0;
                List<Long> commentIds = comments.stream().map(Comment::getId).toList();
                return qaQuestionMapper.selectCount(new LambdaQueryWrapper<QaQuestion>()
                        .eq(QaQuestion::getIsSolved, 1)
                        .in(QaQuestion::getAcceptedCommentId, commentIds));
            }
        } catch (Exception e) {
            log.debug("Failed to count stat for userId={}, rule={}", userId, ruleJson, e);
        }
        return 0;
    }

    private int computeStreak(Long userId) {
        List<CheckinRecord> records = checkinRecordMapper.selectList(
                new LambdaQueryWrapper<CheckinRecord>()
                        .select(CheckinRecord::getCheckinDate)
                        .eq(CheckinRecord::getUserId, userId)
                        .orderByDesc(CheckinRecord::getCheckinDate));
        if (records.isEmpty()) return 0;

        // Get unique sorted dates
        List<LocalDate> dates = records.stream()
                .map(CheckinRecord::getCheckinDate)
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .toList();

        int streak = 1;
        LocalDate today = LocalDate.now();
        LocalDate latest = dates.get(0);
        // Streak only counts if latest checkin is today or yesterday
        if (!latest.equals(today) && !latest.equals(today.minusDays(1))) return 0;

        for (int i = 1; i < dates.size(); i++) {
            if (dates.get(i - 1).minusDays(1).equals(dates.get(i))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private int parseThreshold(String ruleJson) {
        try {
            JsonNode ruleNode = objectMapper.readTree(ruleJson);
            return ruleNode.path("threshold").asInt(1);
        } catch (Exception e) {
            return 1;
        }
    }

    private boolean isAwarded(Long userId, Long achievementId) {
        return userAchievementMapper.selectCount(new LambdaQueryWrapper<UserAchievement>()
                .eq(UserAchievement::getUserId, userId)
                .eq(UserAchievement::getAchievementId, achievementId)) > 0;
    }

    @Transactional
    public void award(Long userId, Long achievementId) {
        if (isAwarded(userId, achievementId)) return;
        UserAchievement ua = new UserAchievement();
        ua.setUserId(userId);
        ua.setAchievementId(achievementId);
        userAchievementMapper.insert(ua);
        Achievement a = achievementMapper.selectById(achievementId);
        log.info("Achievement awarded: userId={}, achievement={}", userId, a != null ? a.getName() : achievementId);
    }
}
