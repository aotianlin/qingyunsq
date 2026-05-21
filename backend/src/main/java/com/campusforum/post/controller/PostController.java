package com.campusforum.post.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.post.dto.CreatePostRequest;
import com.campusforum.post.dto.PostPageRequest;
import com.campusforum.post.dto.PostVO;
import com.campusforum.post.dto.ReactionRequest;
import com.campusforum.post.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @PostMapping
    public R<PostVO> create(@Valid @RequestBody CreatePostRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(postService.create(userId, req));
    }

    @GetMapping
    public R<List<PostVO>> list(PostPageRequest req) {
        return R.ok(postService.page(req));
    }

    @GetMapping("/{id}")
    public R<PostVO> detail(@PathVariable Long id) {
        return R.ok(postService.viewPost(id));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        long userId = StpUtil.getLoginIdAsLong();
        postService.deletePost(userId, id);
        return R.ok();
    }

    @PostMapping("/{id}/reactions")
    public R<Boolean> toggleReaction(@PathVariable Long id, @Valid @RequestBody ReactionRequest req) {
        long userId = StpUtil.getLoginIdAsLong();
        req.setTargetId(id);
        req.setTargetType("POST");
        return R.ok(postService.toggleReaction(userId, req));
    }
}
