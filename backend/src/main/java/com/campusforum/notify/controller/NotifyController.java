package com.campusforum.notify.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.notify.dto.NotificationVO;
import com.campusforum.notify.service.NotifyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @GetMapping
    public R<List<NotificationVO>> list(
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(notifyService.list(userId, cursor, limit));
    }

    @GetMapping("/unread-count")
    public R<Map<String, Long>> getUnreadCount() {
        Long userId = StpUtil.getLoginIdAsLong();
        return R.ok(Map.of("count", notifyService.getUnreadCount(userId)));
    }

    @PutMapping("/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        notifyService.markRead(id, userId);
        return R.ok();
    }

    @PutMapping("/read-all")
    public R<Void> markAllRead() {
        Long userId = StpUtil.getLoginIdAsLong();
        notifyService.markAllRead(userId);
        return R.ok();
    }

    @PutMapping("/batch-read")
    public R<Map<String, Integer>> batchRead(@RequestBody Map<String, List<Long>> body) {
        Long userId = StpUtil.getLoginIdAsLong();
        List<Long> ids = body.getOrDefault("ids", List.of());
        if (ids.size() > 100) {
            return R.fail(400, "批次大小不能超过 100");
        }
        int count = notifyService.batchMarkRead(ids, userId);
        return R.ok(Map.of("count", count));
    }
}
