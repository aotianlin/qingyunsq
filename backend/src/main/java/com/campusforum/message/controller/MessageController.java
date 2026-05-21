package com.campusforum.message.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.message.dto.MessageVO;
import com.campusforum.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public R<MessageVO> send(@RequestBody Map<String, String> body) {
        long senderId = StpUtil.getLoginIdAsLong();
        long receiverId = Long.parseLong(body.get("receiverId"));
        String content = body.get("content");
        String imageUrl = body.get("imageUrl");
        return R.ok(messageService.send(senderId, receiverId, content, imageUrl));
    }

    @GetMapping("/conversations")
    public R<List<MessageVO>> listConversations() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(messageService.listConversations(userId));
    }

    @GetMapping("/conversations/{peerId}")
    public R<List<MessageVO>> getConversation(@PathVariable Long peerId,
                                              @RequestParam(required = false) Long cursor,
                                              @RequestParam(defaultValue = "50") int limit) {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(messageService.getConversation(userId, peerId, cursor, limit));
    }

    @PutMapping("/conversations/{peerId}/read")
    public R<Void> markRead(@PathVariable Long peerId) {
        long userId = StpUtil.getLoginIdAsLong();
        messageService.markRead(userId, peerId);
        return R.ok();
    }

    @GetMapping("/unread-count")
    public R<Long> getUnreadCount() {
        long userId = StpUtil.getLoginIdAsLong();
        return R.ok(messageService.getUnreadCount(userId));
    }

    @PutMapping("/read-all")
    public R<Map<String, Integer>> markAllRead() {
        long userId = StpUtil.getLoginIdAsLong();
        int count = messageService.markAllRead(userId);
        return R.ok(Map.of("count", count));
    }
}
