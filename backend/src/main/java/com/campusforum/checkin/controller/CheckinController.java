package com.campusforum.checkin.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.checkin.dto.*;
import com.campusforum.checkin.service.CheckinService;
import com.campusforum.common.R;
import com.campusforum.post.domain.Post;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping("/challenges")
    public R<CheckinChallengeVO> create(@Valid @RequestBody CreateCheckinChallengeRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(checkinService.create(userId, req));
    }

    @GetMapping("/challenges")
    public R<List<CheckinChallengeVO>> list(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(checkinService.list(spaceId, cursor, limit));
    }

    @GetMapping("/challenges/{id}")
    public R<CheckinChallengeVO> getById(@PathVariable Long id) {
        return R.ok(checkinService.getById(id));
    }

    @PutMapping("/challenges/{id}")
    public R<CheckinChallengeVO> update(@PathVariable Long id,
                                        @Valid @RequestBody CreateCheckinChallengeRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(checkinService.update(id, userId, req));
    }

    @PostMapping("/challenges/{id}/checkin")
    public R<CheckinRecordVO> checkin(@PathVariable Long id,
                                       @Valid @RequestBody CreateCheckinRecordRequest req) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(checkinService.checkin(id, userId, req));
    }

    @GetMapping("/challenges/{id}/records")
    public R<List<CheckinRecordVO>> getRecords(@PathVariable Long id,
                                                @RequestParam(required = false) Long cursor,
                                                @RequestParam(defaultValue = "20") int limit) {
        return R.ok(checkinService.getRecords(id, cursor, limit));
    }

    @GetMapping("/challenges/{id}/leaderboard")
    public R<List<LeaderboardEntry>> getLeaderboard(@PathVariable Long id) {
        return R.ok(checkinService.getLeaderboard(id));
    }

    @DeleteMapping("/challenges/{id}")
    public R<Void> delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        checkinService.delete(id, userId);
        return R.ok();
    }

    @PostMapping("/records/{id}/share")
    public R<Map<String, Object>> shareToSquare(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        Post post = checkinService.shareToSquare(id, userId);
        return R.ok(Map.of("postId", post.getId()));
    }
}
