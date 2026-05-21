package com.campusforum.post.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.post.dto.CommentVO;
import com.campusforum.post.dto.CreateCommentRequest;
import com.campusforum.post.dto.ReactionRequest;
import com.campusforum.post.dto.UpdateCommentRequest;
import com.campusforum.post.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/api/v1/posts/{postId}/comments")
    public R<CommentVO> create(@PathVariable Long postId, @Valid @RequestBody CreateCommentRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        req.setPostId(postId);
        return R.ok(commentService.create(userId, req));
    }

    @GetMapping("/api/v1/posts/{postId}/comments")
    public R<List<CommentVO>> list(@PathVariable Long postId,
                                   @RequestParam(required = false) Long cursor,
                                   @RequestParam(defaultValue = "20") int limit,
                                   @RequestParam(required = false) Boolean qaSort) {
        return R.ok(commentService.listByPost(postId, cursor, limit, qaSort != null && qaSort));
    }

    @DeleteMapping("/api/v1/comments/{id}")
    public R<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        commentService.deleteComment(userId, id);
        return R.ok();
    }

    @PutMapping("/api/v1/comments/{id}")
    public R<CommentVO> update(@PathVariable Long id, @Valid @RequestBody UpdateCommentRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(commentService.updateComment(userId, id, req));
    }

    @PostMapping("/api/v1/comments/{id}/reactions")
    public R<Boolean> toggleReaction(@PathVariable Long id, @Valid @RequestBody ReactionRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        req.setTargetId(id);
        req.setTargetType("COMMENT");
        return R.ok(commentService.toggleReaction(userId, id, req));
    }
}
