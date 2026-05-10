package com.campusforum.notify.service;

import com.campusforum.notify.dto.NotificationVO;
import com.campusforum.user.dto.RegisterRequest;
import com.campusforum.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class NotifyServiceTest {

    @Autowired
    private NotifyService notifyService;

    @Autowired
    private UserService userService;

    private Long userId1;
    private Long userId2;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        RegisterRequest req1 = new RegisterRequest();
        req1.setEmail("notif-u1-" + ts + "@test.com");
        req1.setPassword("Test123456");
        req1.setNickname("通知用户1");
        userId1 = userService.register(req1).getId();

        RegisterRequest req2 = new RegisterRequest();
        req2.setEmail("notif-u2-" + ts + "@test.com");
        req2.setPassword("Test123456");
        req2.setNickname("通知用户2");
        userId2 = userService.register(req2).getId();
    }

    @Test
    void shouldCreateAndList() {
        notifyService.create(userId1, userId2, "LIKE", "点赞通知", "通知用户2 赞了你的帖子", "/posts/1");
        notifyService.create(userId1, userId2, "COMMENT", "评论通知", "通知用户2 评论了你的帖子", "/posts/1");

        List<NotificationVO> list = notifyService.list(userId1, null, 20);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getType()).isEqualTo("COMMENT"); // id DESC, newer first
        assertThat(list.get(1).getType()).isEqualTo("LIKE");
        assertThat(list.get(0).getSender().getId()).isEqualTo(userId2);
    }

    @Test
    void shouldSkipSelfNotification() {
        notifyService.create(userId1, userId1, "LIKE", "点赞通知", "测试", "/posts/1");

        List<NotificationVO> list = notifyService.list(userId1, null, 20);
        assertThat(list).isEmpty();
    }

    @Test
    void shouldCursorPaginate() {
        for (int i = 0; i < 5; i++) {
            notifyService.create(userId1, userId2, "LIKE", "点赞" + i, "内容" + i, "/posts/" + i);
        }

        List<NotificationVO> page1 = notifyService.list(userId1, null, 3);
        assertThat(page1).hasSize(3);

        Long cursor = page1.get(2).getId();
        List<NotificationVO> page2 = notifyService.list(userId1, cursor, 3);
        assertThat(page2).hasSize(2);
        assertThat(page2.get(0).getId()).isLessThan(cursor);
    }

    @Test
    void shouldGetUnreadCount() {
        notifyService.create(userId1, userId2, "LIKE", "点赞", "内容", "/posts/1");
        notifyService.create(userId1, userId2, "COMMENT", "评论", "内容", "/posts/1");

        long count = notifyService.getUnreadCount(userId1);
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldMarkRead() {
        notifyService.create(userId1, userId2, "LIKE", "点赞", "内容", "/posts/1");
        List<NotificationVO> list = notifyService.list(userId1, null, 1);
        Long notifId = list.get(0).getId();
        assertThat(list.get(0).getIsRead()).isFalse();

        notifyService.markRead(notifId, userId1);
        long count = notifyService.getUnreadCount(userId1);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void shouldMarkAllRead() {
        notifyService.create(userId1, userId2, "LIKE", "点赞", "内容1", "/posts/1");
        notifyService.create(userId1, userId2, "COMMENT", "评论", "内容2", "/posts/1");
        assertThat(notifyService.getUnreadCount(userId1)).isEqualTo(2);

        notifyService.markAllRead(userId1);
        assertThat(notifyService.getUnreadCount(userId1)).isEqualTo(0);
    }

    @Test
    void shouldNotMarkReadOfOtherUser() {
        notifyService.create(userId1, userId2, "LIKE", "点赞", "内容", "/posts/1");
        List<NotificationVO> list = notifyService.list(userId1, null, 1);
        Long notifId = list.get(0).getId();

        // user2 试图将发给 user1 的通知标为已读，应被忽略
        notifyService.markRead(notifId, userId2);
        long count = notifyService.getUnreadCount(userId1);
        assertThat(count).isEqualTo(1);
    }
}
