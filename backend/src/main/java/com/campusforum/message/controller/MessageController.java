package com.campusforum.message.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.campusforum.common.R;
import com.campusforum.message.dto.MessageVO;
import com.campusforum.message.dto.SendMessageRequest;
import com.campusforum.message.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 发送私信。
     *
     * <p>对应 bugfix.md 漏洞 17 / T8.7：改用 {@link SendMessageRequest} + {@code @Valid}
     * 替代原 {@code Map<String, String>} 接收方式，receiverId 缺失 / content 超长 /
     * imageUrl 非 http(s) 协议在 controller 层就被 Bean Validation 拦截。</p>
     */
    @PostMapping
    public R<MessageVO> send(@Valid @RequestBody SendMessageRequest req) {
        long senderId = StpUtil.getLoginIdAsLong();
        return R.ok(messageService.send(
                senderId, req.getReceiverId(), req.getContent(), req.getImageUrl()));
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
