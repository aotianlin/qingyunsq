package com.campusforum.ai.service;

import java.util.List;

public interface AiService {
    String summarize(String content);
    RiskResult moderate(String content);
    List<String> recommendTags(String title, String content);
    String chat(List<ChatMessage> messages, String context);

    default String chat(List<ChatMessage> messages, String context, String model) {
        return chat(messages, context);
    }

    /**
     * 生成帖子智能卡片。失败返回 {@code null}，由调用方决定是否兜底。
     *
     * @param title          帖子标题
     * @param content        帖子正文
     * @param postType       发帖时用户自报分类（如 normal/qa/resource/checkin）
     * @param tags           帖子标签（逗号分隔字符串或 null）
     * @param recentComments 最近评论列表（按热度或时间排序，调用方负责截断）
     */
    default PostCardResult generatePostCard(String title,
                                            String content,
                                            String postType,
                                            String tags,
                                            List<String> recentComments) {
        return null;
    }

    /**
     * 检查打卡内容是否与挑战主题相关。
     * @param theme 挑战主题（名称 + 描述）
     * @param content 打卡内容
     * @return true 表示内容符合主题
     */
    default boolean checkRelevance(String theme, String content) {
        return true; // 默认通过
    }

    record RiskResult(int level, String reason) {}
    record ChatMessage(String role, String content) {}

    /**
     * 帖子卡片结构化结果。任何字段都允许 null，由模板根据非空字段渲染。
     */
    record PostCardResult(
            String tldr,
            String audience,
            String valueType,
            Integer readMinutes,
            String commentConsensus,
            String commentDisputes,
            List<String> highlights
    ) {}
}
