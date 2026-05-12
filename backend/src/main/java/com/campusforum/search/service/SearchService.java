package com.campusforum.search.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.resource.domain.Resource;
import com.campusforum.resource.mapper.ResourceMapper;
import com.campusforum.search.dto.SearchResultVO;
import com.campusforum.space.domain.Space;
import com.campusforum.space.mapper.SpaceMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final SpaceMapper spaceMapper;

    public List<SearchResultVO> search(String keyword, String type, String sort, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        String safeKeyword = keyword.replaceAll("[^\\p{L}\\p{N}\\s]", "").strip();
        if (safeKeyword.isBlank()) return List.of();

        if (type != null && !type.isBlank()) {
            return switch (type.toUpperCase()) {
                case "POST" -> searchPosts(safeKeyword, sort, cursor, size);
                case "USER" -> searchUsers(safeKeyword, cursor, size);
                case "RESOURCE" -> searchResources(safeKeyword, cursor, size);
                case "SPACE" -> searchSpaces(safeKeyword, cursor, size);
                default -> List.of();
            };
        }

        // 无 type 则搜索全部
        List<SearchResultVO> results = new ArrayList<>();
        results.addAll(searchPosts(safeKeyword, sort, cursor, size));
        results.addAll(searchUsers(safeKeyword, cursor, size));
        results.addAll(searchResources(safeKeyword, cursor, size));
        results.addAll(searchSpaces(safeKeyword, cursor, size));
        return results;
    }

    private List<SearchResultVO> searchPosts(String keyword, String sort, Long cursor, int limit) {
        LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
        qw.eq(Post::getStatus, 1);
        qw.apply("MATCH(title, content) AGAINST({0} IN NATURAL LANGUAGE MODE)", keyword);
        if (cursor != null) {
            qw.lt(Post::getId, cursor);
        }
        if ("time".equals(sort)) {
            qw.orderByDesc(Post::getCreatedAt);
        } else {
            qw.orderByDesc(Post::getLikeCount, Post::getId);
        }
        qw.last("LIMIT " + limit);

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return postMapper.selectList(qw).stream().map(p -> {
            User author = userMapper.selectById(p.getAuthorId());
            return SearchResultVO.builder()
                    .type("POST")
                    .id(p.getId())
                    .title(p.getTitle())
                    .description(p.getContent().length() > 200 ? p.getContent().substring(0, 200) + "..." : p.getContent())
                    .author(toUserVO(author))
                    .createdAt(p.getCreatedAt())
                    .likeCount(p.getLikeCount())
                    .commentCount(p.getCommentCount())
                    .viewCount(p.getViewCount())
                    .build();
        }).toList();
    }

    private List<SearchResultVO> searchUsers(String keyword, Long cursor, int limit) {
        LambdaQueryWrapper<User> qw = new LambdaQueryWrapper<>();
        qw.eq(User::getStatus, 1);
        qw.and(w -> w.like(User::getNickname, keyword)
                .or().like(User::getEmail, keyword)
                .or().like(User::getStudentNo, keyword));
        if (cursor != null) {
            qw.lt(User::getId, cursor);
        }
        qw.orderByDesc(User::getId);
        qw.last("LIMIT " + limit);

        return userMapper.selectList(qw).stream().map(u -> SearchResultVO.builder()
                .type("USER")
                .id(u.getId())
                .title(u.getNickname())
                .description(u.getCollege() != null ? u.getCollege() + " " + (u.getMajor() != null ? u.getMajor() : "") : "")
                .author(UserVO.builder().id(u.getId()).nickname(u.getNickname()).avatarUrl(u.getAvatarUrl()).build())
                .build()).toList();
    }

    private List<SearchResultVO> searchResources(String keyword, Long cursor, int limit) {
        LambdaQueryWrapper<Resource> qw = new LambdaQueryWrapper<>();
        qw.eq(Resource::getStatus, 1);
        qw.and(w -> w.like(Resource::getFileName, keyword)
                .or().like(Resource::getDescription, keyword));
        if (cursor != null) {
            qw.lt(Resource::getId, cursor);
        }
        qw.orderByDesc(Resource::getId);
        qw.last("LIMIT " + limit);

        return resourceMapper.selectList(qw).stream().map(r -> {
            User uploader = userMapper.selectById(r.getUploaderId());
            return SearchResultVO.builder()
                    .type("RESOURCE")
                    .id(r.getId())
                    .title(r.getFileName())
                    .description(r.getDescription())
                    .author(toUserVO(uploader))
                    .createdAt(r.getCreatedAt())
                    .downloadCount(r.getDownloadCount())
                    .fileType(r.getFileType())
                    .fileSize(r.getFileSize())
                    .build();
        }).toList();
    }

    private List<SearchResultVO> searchSpaces(String keyword, Long cursor, int limit) {
        LambdaQueryWrapper<Space> qw = new LambdaQueryWrapper<>();
        qw.eq(Space::getStatus, 1);
        qw.and(w -> w.like(Space::getName, keyword)
                .or().like(Space::getDescription, keyword));
        if (cursor != null) {
            qw.lt(Space::getId, cursor);
        }
        qw.orderByDesc(Space::getMemberCount, Space::getId);
        qw.last("LIMIT " + limit);

        return spaceMapper.selectList(qw).stream().map(s -> {
            User owner = userMapper.selectById(s.getOwnerId());
            return SearchResultVO.builder()
                    .type("SPACE")
                    .id(s.getId())
                    .title(s.getName())
                    .description(s.getDescription())
                    .author(toUserVO(owner))
                    .createdAt(s.getCreatedAt())
                    .category(s.getCategory())
                    .memberCount(s.getMemberCount())
                    .postCount(s.getPostCount())
                    .build();
        }).toList();
    }

    private UserVO toUserVO(User user) {
        if (user == null) return null;
        return UserVO.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
