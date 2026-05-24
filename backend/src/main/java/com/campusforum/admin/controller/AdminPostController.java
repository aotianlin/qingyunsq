package com.campusforum.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.infra.audit.AuditLogService;
import com.campusforum.common.R;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final PostService postService;
    private final AuditLogService auditLogService;

    @GetMapping
    @SaCheckPermission("tenant:post:manage")
    public R<List<PostVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(postService.listPostsForAdmin(keyword, status, scope, cursor, limit));
    }

    @PutMapping("/{id}/pin")
    @SaCheckPermission("tenant:post:manage")
    public R<Void> togglePin(@PathVariable Long id) {
        postService.togglePin(id);
        auditLogService.log("POST_PIN", "post", id,
                "post pin toggled by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @PutMapping("/{id}/essence")
    @SaCheckPermission("tenant:post:manage")
    public R<Void> toggleEssence(@PathVariable Long id) {
        postService.toggleEssence(id);
        auditLogService.log("POST_ESSENCE", "post", id,
                "post essence toggled by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("tenant:post:manage")
    public R<Void> setStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        Integer newStatus = body.get("status");
        postService.setStatus(id, newStatus);
        auditLogService.log("POST_STATUS", "post", id,
                "post status set to " + newStatus + " by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("tenant:post:manage")
    public R<Void> forceDelete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        postService.deletePost(userId, id);
        auditLogService.log("POST_FORCE_DELETE", "post", id,
                "post force deleted by admin " + StpUtil.getLoginIdAsLong());
        return R.ok();
    }
}
