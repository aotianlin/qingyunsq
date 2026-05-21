package com.campusforum.space.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.service.PostService;
import com.campusforum.space.dto.*;
import com.campusforum.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;
    private final PostService postService;

    @PostMapping
    public R<SpaceVO> create(@Valid @RequestBody CreateSpaceRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(spaceService.create(userId, req));
    }

    @GetMapping
    public R<List<SpaceVO>> list(@RequestParam(required = false) String category,
                                  @RequestParam(required = false) Long cursor,
                                  @RequestParam(defaultValue = "20") int limit) {
        return R.ok(spaceService.list(category, cursor, limit));
    }

    @GetMapping("/{id}")
    public R<SpaceVO> detail(@PathVariable Long id) {
        return R.ok(spaceService.getById(id));
    }

    @PutMapping("/{id}")
    public R<SpaceVO> update(@PathVariable Long id, @Valid @RequestBody UpdateSpaceRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(spaceService.update(id, userId, req));
    }

    @PostMapping("/{id}/join")
    public R<SpaceVO> join(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(spaceService.join(id, userId));
    }

    @PostMapping("/{id}/leave")
    public R<Void> leave(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        spaceService.leave(id, userId);
        return R.ok();
    }

    @GetMapping("/{id}/members")
    public R<List<SpaceMemberVO>> members(@PathVariable Long id,
                                           @RequestParam(required = false) Long cursor,
                                           @RequestParam(defaultValue = "20") int limit) {
        return R.ok(spaceService.listMembers(id, cursor, limit));
    }

    @PutMapping("/{id}/members/{userId}")
    public R<Void> handleMember(@PathVariable Long id,
                                 @PathVariable Long userId,
                                 @RequestParam String action) {
        long operatorId = StpUtil.getLoginIdAsLong();
        if ("approve".equals(action)) {
            spaceService.approveMember(id, operatorId, userId);
        } else if ("remove".equals(action)) {
            spaceService.removeMember(id, operatorId, userId);
        }
        return R.ok();
    }

    @GetMapping("/{id}/posts")
    public R<List<PostVO>> spacePosts(@PathVariable Long id,
                                       @RequestParam(required = false) Long cursor,
                                       @RequestParam(defaultValue = "20") int limit) {
        // Bug fix 1.6: 私有空间访问校验
        Long userId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        spaceService.checkMemberAccess(id, userId);
        return R.ok(postService.pageBySpace(id, false, cursor, limit));
    }

    @GetMapping("/{id}/posts/all")
    public R<List<PostVO>> spacePostsAll(@PathVariable Long id,
                                          @RequestParam(required = false) Long cursor,
                                          @RequestParam(defaultValue = "20") int limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        spaceService.checkSpaceAdmin(id, userId);
        return R.ok(postService.pageBySpace(id, true, cursor, limit));
    }

    @PutMapping("/{id}/posts/{postId}/status")
    public R<Void> setPostStatus(@PathVariable Long id,
                                  @PathVariable Long postId,
                                  @RequestParam Integer status) {
        Long userId = StpUtil.getLoginIdAsLong();
        spaceService.checkSpaceAdmin(id, userId);
        // Bug fix 1.7: 校验帖子归属空间
        postService.setStatusForSpace(postId, id, status);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> dismiss(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        spaceService.dismiss(id, userId);
        return R.ok();
    }
}
