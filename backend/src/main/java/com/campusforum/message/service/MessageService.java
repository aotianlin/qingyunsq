package com.campusforum.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.message.domain.Message;
import com.campusforum.message.dto.MessageVO;
import com.campusforum.message.mapper.MessageMapper;
import com.campusforum.notify.websocket.SessionRegistry;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.PublicUserVO;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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

        // Bug fix 1.9: 使用 ObjectMapper 安全构造 JSON，防止注入
        try {
            User sender = userMapper.selectById(senderId);
            String senderName = sender != null ? sender.getNickname() : "有人";
            Map<String, Object> payloadMap = new LinkedHashMap<>();
            payloadMap.put("type", "MESSAGE");
            payloadMap.put("senderId", senderId);
            payloadMap.put("senderName", senderName);
            payloadMap.put("content", content != null ? content.substring(0, Math.min(content.length(), 50)) : "[图片]");
            String payload = objectMapper.writeValueAsString(payloadMap);
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
     * Bug fix 1.16: 使用 SQL 分组分页替代全量加载
     */
    public List<MessageVO> listConversations(Long userId) {
        List<Long> latestIds = messageMapper.selectLatestMessageIdsPerConversation(userId, 50);
        if (latestIds.isEmpty()) return List.of();
        List<Message> messages = messageMapper.selectBatchIds(latestIds);
        // 按 ID 倒序排列
        messages.sort((a, b) -> Long.compare(b.getId(), a.getId()));
        return messages.stream().map(this::toVO).toList();
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

    @Transactional
    public int markAllRead(Long userId) {
        LambdaQueryWrapper<Message> qw = new LambdaQueryWrapper<>();
        qw.eq(Message::getReceiverId, userId)
          .eq(Message::getIsRead, 0);
        List<Message> unread = messageMapper.selectList(qw);
        for (Message m : unread) {
            m.setIsRead(1);
            messageMapper.updateById(m);
        }
        return unread.size();
    }

    private MessageVO toVO(Message m) {
        User sender = userMapper.selectById(m.getSenderId());
        PublicUserVO senderVO = PublicUserVO.from(sender);
        // 同时返回接收者信息，用于对话列表正确显示对方头像和昵称
        User receiver = userMapper.selectById(m.getReceiverId());
        PublicUserVO receiverVO = PublicUserVO.from(receiver);
        return MessageVO.builder()
                .id(m.getId())
                .senderId(m.getSenderId())
                .receiverId(m.getReceiverId())
                .sender(senderVO)
                .receiver(receiverVO)
                .content(m.getContent())
                .imageUrl(m.getImageUrl())
                .isRead(m.getIsRead() != null && m.getIsRead() == 1)
                .createdAt(m.getCreatedAt())
                .build();
    }
}
