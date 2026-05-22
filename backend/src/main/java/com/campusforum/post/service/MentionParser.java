package com.campusforum.post.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从内容中解析 @username 提及，去重后返回昵称集合。
 *
 * <p>安全加固（缺陷 1.31）：限制单条内容最多解析 20 个 mention，
 * 避免攻击者构造数千个 @ 触发通知放大、邮件轰炸等下游成本。</p>
 */
public final class MentionParser {

    /** 匹配 @昵称：中文/英文/数字/下划线/连字符，1-30 字符 */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w\\u4e00-\\u9fa5-]{1,30})");

    /** 单条内容最多解析的 mention 数量上限。 */
    private static final int MAX_MENTIONS = 20;

    private MentionParser() {}

    /**
     * 提取内容中所有被 @ 的用户昵称（去重）。
     * 超过 {@value #MAX_MENTIONS} 个时静默截断，仅保留前 N 个。
     */
    public static Set<String> extract(String content) {
        if (content == null || content.isBlank()) return Set.of();
        Set<String> names = new LinkedHashSet<>();
        Matcher m = MENTION_PATTERN.matcher(content);
        while (m.find() && names.size() < MAX_MENTIONS) {
            names.add(m.group(1));
        }
        return names;
    }
}
