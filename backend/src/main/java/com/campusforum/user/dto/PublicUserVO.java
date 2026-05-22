package com.campusforum.user.dto;

import com.campusforum.user.domain.User;
import lombok.Builder;
import lombok.Data;

/**
 * 公开场景下的精简用户视图。
 *
 * <p>安全加固（缺陷 1.21）：原本 {@link UserVO} 在帖子作者、评论作者、消息发件人、
 * 资源上传者等公共列表中被序列化时会回传 {@code email} 字段，违反最小披露原则将同租户
 * 所有用户邮箱泄漏。本 VO 仅保留 {@code id/nickname/avatarUrl}，{@link UserVO} 则只在
 * "本人详情"接口中使用。</p>
 */
@Data
@Builder
public class PublicUserVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    /** 个人简介，公开场景可见，无 PII 风险 */
    private String bio;

    /** 工厂方法，从 User 实体直接构造；user 为 null 时返回 null。 */
    public static PublicUserVO from(User user) {
        if (user == null) return null;
        return PublicUserVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .build();
    }
}
