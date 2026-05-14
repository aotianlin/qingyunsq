package com.campusforum.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.message.domain.Message;
import com.campusforum.message.dto.MessageVO;
import com.campusforum.message.mapper.MessageMapper;
import com.campusforum.notify.websocket.SessionRegistry;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageMapper messageMapper;
    private final UserMapper userMapper;
    private final SessionRegistry sessionRegistry;

    @Transactional
    public MessageVO send(Long senderId, Long receiverId, String content, String imageUrl) {
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不能给自己发私信");
        }
        User receiver = userMapper.selectById(receiverId);
        if (receiver == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        Message msg = new Message();
        msg.setSenderId(senderId);
        msg.setReceiverId(receiverId);
        msg.setContent(content);
        msg.setImageUrl(imageUrl);
        msg.setIsRead(0);
        messageMapper.insert(msg);

        // WebSocket 实时推送
        try {
            User sender = userMapper.selectById(senderId);
            String senderName = sender != null ? sender.getNickname() : "有人";
            String payload = "{\"type\":\"MESSAGE\",\"senderId\":" + senderId +
                    ",\"senderName\":\"" + senderName + "\",\"content\":\"" +
                    (content != null ? content.substring(0, Math.min(content.length(), 50)) : "[图片]") + "\"}";
            sessionRegistry.sendToUser(receiverId, payload);
        } catch (Exception ignored) {}

        return toVO(msg);
    }

    public List<MessageVO> getConversation(Long userId, Long peerId, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w
                .and(a -> a.eq(Message::getSenderId, userId).eq(Message::getReceiverId, peerId))
                .or(a -> a.eq(Message::getSenderId, peerId).eq(Message::getReceiverId, userId)));
        if (cursor != null) {
            qw.lt(Message::getId, cursor);
        }
        qw.orderByDesc(Message::getId);
        qw.last("LIMIT " + size);

        List<Message> list = messageMapper.selectList(qw);
        // 按时间正序显示
        Collections.reverse(list);
        return list.stream().map(this::toVO).toList();
    }

    /**
     * 获取所有对话列表，每条对话显示最后一条消息。
     */
    public List<MessageVO> listConversations(Long userId) {
        // 查所有我发送或接收的消息，按 id 倒序
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.eq(Message::getSenderId, userId).or().eq(Message::getReceiverId, userId));
        qw.orderByDesc(Message::getId);
        List<Message> all = messageMapper.selectList(qw);

        // 按对话对方去重，保留最新一条
        Map<String, Message> latestMap = new LinkedHashMap<>();
        Set<Long> seenPeers = new LinkedHashSet<>();
        for (Message m : all) {
            long peerId = m.getSenderId().equals(userId) ? m.getReceiverId() : m.getSenderId();
            if (!seenPeers.contains(peerId)) {
                seenPeers.add(peerId);
                latestMap.put(String.valueOf(peerId), m);
            }
        }

        return latestMap.values().stream().map(this::toVO).toList();
    }

    @Transactional
    public void markRead(Long userId, Long peerId) {
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        qw.eq(Message::getReceiverId, userId)
          .eq(Message::getSenderId, peerId)
          .eq(Message::getIsRead, 0);
        List<Message> unread = messageMapper.selectList(qw);
        for (Message m : unread) {
            m.setIsRead(1);
            messageMapper.updateById(m);
        }
    }

    public long getUnreadCount(Long userId) {
        return messageMapper.selectCount(new LambdaQueryWrapper<Message>()
                .eq(Message::getReceiverId, userId)
                .eq(Message::getIsRead, 0));
    }

    private MessageVO toVO(Message m) {
        User sender = userMapper.selectById(m.getSenderId());
        UserVO senderVO = null;
        if (sender != null) {
            senderVO = UserVO.builder()
                    .id(sender.getId())
                    .nickname(sender.getNickname())
                    .avatarUrl(sender.getAvatarUrl())
                    .build();
        }
        return MessageVO.builder()
                .id(m.getId())
                .senderId(m.getSenderId())
                .receiverId(m.getReceiverId())
                .sender(senderVO)
                .content(m.getContent())
                .imageUrl(m.getImageUrl())
                .isRead(m.getIsRead() != null && m.getIsRead() == 1)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
