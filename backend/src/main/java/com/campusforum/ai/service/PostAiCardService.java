package com.campusforum.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.ai.domain.PostAiCard;
import com.campusforum.ai.mapper.PostAiCardMapper;
import com.campusforum.post.domain.Comment;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.CommentMapper;
import com.campusforum.post.mapper.PostMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;

/**
 * 帖子智能卡片：按需生成 + 缓存。
 *
 * <p>触发刷新条件：</p>
 * <ul>
 *   <li>缓存不存在</li>
 *   <li>帖子 updated_at 比缓存 post_version 新</li>
 *   <li>评论数比 snapshot 多 10 条以上</li>
 * </ul>
 *
 * <p>AI 调用失败时，方法返回 {@code null}，不写入缓存；下次浏览自动重试。
 * 这样做避免脏数据沉淀，但需要前端在 null 时优雅隐藏卡片。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAiCardService {

    /** 评论增量阈值，超过则触发评论部分重新生成。 */
    private static final int COMMENT_REGEN_THRESHOLD = 10;

    private final PostAiCardMapper cardMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final AiService aiService;

    /**
     * 仅读取缓存，不触发 AI 生成。用于列表页等需要轻量读取的场景。
     */
    public PostAiCard getCached(Long postId) {
        if (postId == null) return null;
        return cardMapper.selectOne(
                new LambdaQueryWrapper<PostAiCard>().eq(PostAiCard::getPostId, postId));
    }

    /**
     * 获取或生成帖子卡片。返回 {@code null} 表示生成失败 / 帖子不存在；前端据此隐藏卡片。
     */
    @Transactional
    public PostAiCard getOrGenerate(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) return null;

        PostAiCard cached = cardMapper.selectOne(
                new LambdaQueryWrapper<PostAiCard>().eq(PostAiCard::getPostId, postId));

        long postVersion = post.getUpdatedAt() == null ? 0
                : post.getUpdatedAt().toEpochSecond(ZoneOffset.UTC) * 1000;
        int currentCommentCount = post.getCommentCount() == null ? 0 : post.getCommentCount();

        boolean needRegen = cached == null
                || !equalsLong(cached.getPostVersion(), postVersion)
                || (currentCommentCount - safeInt(cached.getCommentCountSnapshot()) >= COMMENT_REGEN_THRESHOLD);

        if (!needRegen) {
            return cached;
        }

        List<String> recentComments = fetchRecentComments(postId);
        AiService.PostCardResult result;
        try {
            result = aiService.generatePostCard(
                    post.getTitle(),
                    post.getContent(),
                    post.getType(),
                    post.getTags(),
                    recentComments);
        } catch (Exception e) {
            log.warn("AI generatePostCard threw: postId={}, err={}", postId, e.getClass().getSimpleName());
            return cached; // 失败时返回旧缓存（可能 null），前端隐藏
        }

        if (result == null) {
            return cached;
        }

        // 取点赞最多评论作为「热门评论」，列表行展示并提供跳转锚点
        Comment hot = pickHotComment(postId);

        PostAiCard entity = cached != null ? cached : new PostAiCard();
        entity.setPostId(postId);
        entity.setTldr(result.tldr());
        entity.setAudience(result.audience());
        entity.setValueType(result.valueType());
        entity.setReadMinutes(result.readMinutes());
        entity.setCommentConsensus(result.commentConsensus());
        entity.setCommentDisputes(result.commentDisputes());
        entity.setHotCommentId(hot == null ? null : hot.getId());
        entity.setHotCommentExcerpt(hot == null ? null : excerpt(hot.getContent(), 80));
        entity.setHighlights(serializeHighlights(result.highlights()));
        entity.setPostVersion(postVersion);
        entity.setCommentCountSnapshot(currentCommentCount);

        if (cached == null) {
            cardMapper.insert(entity);
        } else {
            cardMapper.updateById(entity);
        }
        return entity;
    }

    /**
     * 批量读缓存，列表页用。不存在的 postId 不在返回 map 中。
     */
    public java.util.Map<Long, PostAiCard> getCachedBatch(java.util.List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) return java.util.Collections.emptyMap();
        java.util.List<PostAiCard> list = cardMapper.selectList(
                new LambdaQueryWrapper<PostAiCard>().in(PostAiCard::getPostId, postIds));
        java.util.Map<Long, PostAiCard> map = new java.util.HashMap<>();
        for (PostAiCard c : list) {
            map.put(c.getPostId(), c);
        }
        return map;
    }

    /** 取点赞最多的有效评论；无赞时取最新一条；无任何评论返回 null。 */
    private Comment pickHotComment(Long postId) {
        java.util.List<Comment> list = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .orderByDesc(Comment::getLikeCount)
                        .orderByDesc(Comment::getCreatedAt)
                        .last("LIMIT 1"));
        if (list.isEmpty()) return null;
        Comment c = list.get(0);
        if (c.getContent() == null || c.getContent().isBlank()) return null;
        return c;
    }

    private static String excerpt(String text, int maxLen) {
        if (text == null) return null;
        String cleaned = text.replaceAll("\\s+", " ").strip();
        if (cleaned.length() <= maxLen) return cleaned;
        return cleaned.substring(0, maxLen) + "…";
    }

    /** highlights 是 List<String>，序列化为 JSON 字符串存 DB；为空时返回 null。 */
    private String serializeHighlights(java.util.List<String> highlights) {
        if (highlights == null || highlights.isEmpty()) return null;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(highlights);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取最近 20 条非空评论。简单按时间倒序，未来可以换成"按点赞数排序"做"高赞"摘要。
     */
    private List<String> fetchRecentComments(Long postId) {
        return commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 1)
                        .orderByDesc(Comment::getLikeCount)
                        .orderByDesc(Comment::getCreatedAt)
                        .last("LIMIT 20"))
                .stream()
                .map(Comment::getContent)
                .filter(c -> c != null && !c.isBlank())
                .toList();
    }

    private static boolean equalsLong(Long a, long b) {
        return a != null && a == b;
    }

    private static int safeInt(Integer v) {
        return v == null ? 0 : v;
    }
}
