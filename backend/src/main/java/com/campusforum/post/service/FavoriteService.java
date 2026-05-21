package com.campusforum.post.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.post.domain.Post;
import com.campusforum.post.domain.Reaction;
import com.campusforum.post.dto.FavoriteVO;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.post.mapper.ReactionMapper;
import com.campusforum.resource.domain.Resource;
import com.campusforum.resource.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final ReactionMapper reactionMapper;
    private final PostMapper postMapper;
    private final ResourceMapper resourceMapper;

    private static final Set<String> VALID_TARGET_TYPES = Set.of("POST", "COMMENT", "RESOURCE");

    public List<FavoriteVO> listFavorites(Long userId, String targetType, Long cursor, int limit) {
        int size = Math.min(Math.max(limit, 1), 50);

        // 验证 targetType
        if (targetType != null && !targetType.isBlank() && !VALID_TARGET_TYPES.contains(targetType.toUpperCase())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不支持的 targetType: " + targetType);
        }

        LambdaQueryWrapper<Reaction> qw = new LambdaQueryWrapper<>();
        qw.eq(Reaction::getUserId, userId);
        qw.eq(Reaction::getType, "COLLECT");

        if (targetType != null && !targetType.isBlank()) {
            qw.eq(Reaction::getTargetType, targetType.toUpperCase());
        }
        if (cursor != null) {
            qw.lt(Reaction::getId, cursor);
        }
        qw.orderByDesc(Reaction::getId);
        qw.last("LIMIT " + size);

        List<Reaction> reactions = reactionMapper.selectList(qw);

        return reactions.stream()
                .map(this::toFavoriteVO)
                .filter(vo -> vo != null) // 排除已删除的目标
                .toList();
    }

    private FavoriteVO toFavoriteVO(Reaction reaction) {
        FavoriteVO.FavoriteVOBuilder builder = FavoriteVO.builder()
                .id(reaction.getId())
                .targetType(reaction.getTargetType())
                .targetId(reaction.getTargetId())
                .collectedAt(reaction.getCreatedAt());

        if ("POST".equals(reaction.getTargetType())) {
            Post post = postMapper.selectById(reaction.getTargetId());
            if (post == null || post.getDeleted() == 1) return null; // 已删除，排除
            builder.postTitle(post.getTitle());
            builder.postContentPreview(truncate(post.getContent(), 100));
        } else if ("RESOURCE".equals(reaction.getTargetType())) {
            Resource resource = resourceMapper.selectById(reaction.getTargetId());
            if (resource == null || resource.getDeleted() == 1) return null; // 已删除，排除
            builder.resourceFileName(resource.getFileName());
            builder.resourceFileType(resource.getFileType());
        }
        // COMMENT 类型暂不做额外填充

        return builder.build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
