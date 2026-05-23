package com.campusforum.ai.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.campusforum.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 帖子 AI 智能卡片缓存。
 *
 * <p>由 {@code PostAiCardService} 按需生成与刷新，
 * 提供帖子详情页顶部的 TL;DR + 适合谁读 + 价值类型 + 评论共识/争议。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("post_ai_cards")
public class PostAiCard extends BaseEntity {

    private Long postId;

    private String tldr;
    private String audience;
    private String valueType;
    private Integer readMinutes;

    private String commentConsensus;
    private String commentDisputes;

    /** 当前帖子点赞最多的评论 ID，用于跳转锚点。无评论时为 null。 */
    private Long hotCommentId;
    /** 热门评论摘录（截断 80 字）。 */
    private String hotCommentExcerpt;

    /** AI 提取重点关键词，JSON 数组字符串，如 ["六级备考","词汇方法","真题"]。 */
    private String highlights;

    /** 生成时帖子 updated_at 的时间戳（毫秒）。帖子更新后比对此字段决定是否重新生成。 */
    private Long postVersion;

    /** 生成时帖子的评论数。比对当前 commentCount 决定是否仅刷新评论部分。 */
    private Integer commentCountSnapshot;
}
