package com.campusforum.ai.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@ConditionalOnProperty(name = "ai.provider", havingValue = "mock", matchIfMissing = true)
public class MockAiService implements AiService {

    private static final Set<String> RISK_KEYWORDS = Set.of(
            "作弊", "代考", "替考", "赌博", "裸聊", "嫖娼", "毒品",
            "广告推广", "刷单", "兼职刷单", "网贷", "校园贷"
    );

    @Override
    public String summarize(String content) {
        if (content == null || content.isBlank()) return "";
        String cleaned = content.replaceAll("\\s+", " ").strip();
        return cleaned.length() <= 150 ? cleaned : cleaned.substring(0, 150) + "...";
    }

    @Override
    public RiskResult moderate(String content) {
        if (content == null || content.isBlank()) return new RiskResult(0, "");
        String lower = content.toLowerCase();
        for (String kw : RISK_KEYWORDS) {
            if (lower.contains(kw)) {
                return new RiskResult(2, "内容包含敏感词: " + kw);
            }
        }
        if (content.length() > 5000) {
            return new RiskResult(1, "内容过长，建议精简");
        }
        return new RiskResult(0, "");
    }

    @Override
    public List<String> recommendTags(String title, String content) {
        String text = (title != null ? title + " " : "") + (content != null ? content : "");
        if (text.isBlank()) return List.of();

        // 提取中文/英文连续片段
        String cleaned = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
        if (cleaned.length() < 2) return List.of();

        // 用 2-4 字滑动窗口统计 n-gram 频率
        Map<String, Long> freq = new LinkedHashMap<>();
        for (int len = 4; len >= 2; len--) {
            for (int i = 0; i + len <= cleaned.length(); i++) {
                String ngram = cleaned.substring(i, i + len).toLowerCase();
                freq.merge(ngram, 1L, Long::sum);
            }
        }

        Set<String> stopWords = Set.of("这是", "一个", "可以", "这个", "那个", "什么", "怎么", "为什么", "因为", "所以", "但是", "如果", "然后", "或者", "而且", "虽然", "不过", "已经", "正在", "将要", "还是", "我们", "他们", "自己", "没有");
        return freq.entrySet().stream()
                .filter(e -> !stopWords.contains(e.getKey()))
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public String chat(List<ChatMessage> messages, String context) {
        if (messages == null || messages.isEmpty()) {
            return "你好！我是 CampusForum AI 助手。有什么可以帮助你的吗？";
        }
        ChatMessage last = messages.get(messages.size() - 1);
        String question = last.content().toLowerCase();

        if (question.contains("搜索") || question.contains("查找") || question.contains("寻找")) {
            return "你可以使用页面上方的搜索功能，输入关键词来查找帖子、用户、资源或学习空间。";
        }
        if (question.contains("发帖") || question.contains("发布") || question.contains("创建")) {
            return "点击页面上的「发帖」按钮，选择帖子类型（普通/问答/资源/打卡），填写标题和内容后即可发布。";
        }
        if (question.contains("积分") || question.contains("悬赏") || question.contains("奖励")) {
            return "积分可以通过每日登录、发帖、回答问题被采纳、打卡等行为获得。悬赏积分会从你的账户中扣除并转给被采纳者。";
        }
        if (question.contains("空间") || question.contains("加入")) {
            return "你可以在「学习空间」页面浏览和搜索感兴趣的空间。公开空间可以直接加入，审核制空间需要空间主或管理员审核。";
        }
        if (question.contains("打卡") || question.contains("挑战")) {
            return "在「自律打卡」页面可以创建或参与打卡挑战。每天坚持打卡可以积累连续天数，在排行榜上展示你的坚持。";
        }
        return "这是 CampusForum AI 助手的默认回复。你可以问我关于平台使用的任何问题，比如如何发帖、搜索内容、管理空间等。";
    }
}
