package com.campusforum.ai.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
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
        if (context != null && context.contains("【站内检索资料】")) {
            String question = messages == null || messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
            String sources = extractRagSources(context);
            if (!sources.isBlank()) {
                return "我已结合站内检索资料回答你的问题：" + question + "\n\n"
                        + "本地 mock 模式不会调用外部大模型，但已经完成检索增强链路，召回到的资料如下：\n"
                        + sources;
            }
            return "我已尝试进行站内检索，但没有召回到可确认的资料。可以换一个更具体的课程、标签、资源名或帖子关键词再问。";
        }

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

    @Override
    public PostCardResult generatePostCard(String title,
                                           String content,
                                           String postType,
                                           String tags,
                                           List<String> recentComments) {
        if ((title == null || title.isBlank()) && (content == null || content.isBlank())) {
            return null;
        }
        String tldrSource = (title != null ? title : "");
        if (content != null && !content.isBlank()) {
            String body = content.replaceAll("\\s+", " ").strip();
            tldrSource = tldrSource.isBlank() ? body : tldrSource + "：" + body;
        }
        String tldr = tldrSource.length() <= 60 ? tldrSource : tldrSource.substring(0, 60) + "…";

        String valueType = switch (postType == null ? "" : postType.toLowerCase()) {
            case "qa", "question" -> "提问";
            case "resource" -> "资源";
            case "checkin" -> "打卡";
            case "experience", "share" -> "经验";
            default -> "讨论";
        };

        int chars = (content == null ? 0 : content.length());
        int readMinutes = Math.max(1, (int) Math.ceil(chars / 400.0));

        String audience = inferAudience(title, content, tags);

        String consensus = null;
        String disputes = null;
        if (recentComments != null && recentComments.size() >= 3) {
            // Mock 模式不做语义聚类，只取一条最长评论摘要替代"共识"
            String longest = recentComments.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .max((a, b) -> Integer.compare(a.length(), b.length()))
                    .orElse(null);
            if (longest != null) {
                consensus = longest.length() <= 120 ? longest : longest.substring(0, 120) + "…";
            }
            if (recentComments.size() >= 8) {
                disputes = "评论较多，可能存在多种观点（mock 模式不做语义聚合）";
            }
        }

        return new PostCardResult(tldr, audience, valueType, readMinutes, consensus, disputes,
                recommendTags(title, content));
    }

    private String inferAudience(String title, String content, String tags) {
        String text = (title == null ? "" : title) + " " + (content == null ? "" : content) + " " + (tags == null ? "" : tags);
        String lower = text.toLowerCase();
        if (lower.contains("考研") || lower.contains("保研")) return "正在备考升学的同学";
        if (lower.contains("实习") || lower.contains("校招") || lower.contains("面试")) return "找工作或实习的同学";
        if (lower.contains("毕设") || lower.contains("毕业设计")) return "正在做毕设的同学";
        if (lower.contains("入门") || lower.contains("新手") || lower.contains("小白")) return "想入门该方向的同学";
        if (lower.contains("踩坑") || lower.contains("避坑") || lower.contains("教训")) return "想避开同类问题的同学";
        if (lower.contains("教程") || lower.contains("笔记")) return "想系统学习该主题的同学";
        return "对该话题感兴趣的同学";
    }

    @Override
    public boolean checkRelevance(String theme, String content) {
        if (theme == null || theme.isBlank() || content == null || content.isBlank()) return true;
        String themeLower = theme.toLowerCase();
        String contentLower = content.toLowerCase();
        // 从主题提取关键词（2+ 字的中文词或 3+ 字母的英文词）
        List<String> keywords = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\u4e00-\\u9fa5]{2,}|[a-zA-Z]{3,}")
                .matcher(themeLower);
        while (m.find()) keywords.add(m.group());
        if (keywords.isEmpty()) return true;
        // 至少有一个关键词出现在内容中
        for (String kw : keywords) {
            if (contentLower.contains(kw)) return true;
        }
        return false;
    }

    private String extractRagSources(String context) {
        // RAG 服务会按 “[编号] 类型 标题” 拼接来源；mock 环境提取这些行，方便本地直接验证召回是否生效。
        StringBuilder sources = new StringBuilder();
        for (String line : context.split("\\R")) {
            if (line.matches("\\[\\d+]\\s+.*")) {
                if (!sources.isEmpty()) {
                    sources.append('\n');
                }
                sources.append(line);
            }
        }
        return sources.toString();
    }
}
