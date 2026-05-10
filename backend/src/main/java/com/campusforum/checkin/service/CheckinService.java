package com.campusforum.checkin.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.checkin.domain.CheckinChallenge;
import com.campusforum.checkin.domain.CheckinRecord;
import com.campusforum.checkin.dto.*;
import com.campusforum.checkin.mapper.CheckinChallengeMapper;
import com.campusforum.checkin.mapper.CheckinRecordMapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final CheckinChallengeMapper challengeMapper;
    private final CheckinRecordMapper recordMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public CheckinChallengeVO create(Long userId, CreateCheckinChallengeRequest req) {
        CheckinChallenge challenge = new CheckinChallenge();
        challenge.setCreatorId(userId);
        challenge.setName(req.getName());
        challenge.setDescription(req.getDescription());
        challenge.setSpaceId(req.getSpaceId());
        challenge.setStartDate(req.getStartDate());
        challenge.setEndDate(req.getEndDate());
        challenge.setRule(req.getRule());
        challenge.setMemberCount(0);
        challenge.setStatus(1);

        challengeMapper.insert(challenge);
        log.info("Checkin challenge created: id={}, name={}", challenge.getId(), challenge.getName());
        return toVO(challenge, userId, false, 0, 0);
    }

    public CheckinChallengeVO getById(Long challengeId) {
        CheckinChallenge challenge = challengeMapper.selectById(challengeId);
        if (challenge == null || challenge.getStatus() == 0) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
        }

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        boolean isMember = false;
        int totalDays = 0;
        int streak = 0;

        if (currentUserId != null) {
            List<CheckinRecord> records = recordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                    .eq(CheckinRecord::getChallengeId, challengeId)
                    .eq(CheckinRecord::getUserId, currentUserId));
            if (!records.isEmpty()) {
                isMember = true;
                totalDays = records.size();
                streak = calcStreak(records);
            }
        }

        return toVO(challenge, currentUserId, isMember, totalDays, streak);
    }

    public List<CheckinChallengeVO> list(Long spaceId, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<CheckinChallenge> qw = new LambdaQueryWrapper<>();
        qw.eq(CheckinChallenge::getStatus, 1);
        if (spaceId != null) {
            qw.eq(CheckinChallenge::getSpaceId, spaceId);
        }
        if (cursor != null) {
            qw.lt(CheckinChallenge::getId, cursor);
        }
        qw.orderByDesc(CheckinChallenge::getMemberCount, CheckinChallenge::getId);
        qw.last("LIMIT " + size);

        List<CheckinChallenge> challenges = challengeMapper.selectList(qw);
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return challenges.stream().map(c -> toVO(c, currentUserId, false, 0, 0)).toList();
    }

    @Transactional
    public CheckinChallengeVO update(Long challengeId, Long userId, CreateCheckinChallengeRequest req) {
        CheckinChallenge challenge = challengeMapper.selectById(challengeId);
        if (challenge == null || challenge.getStatus() == 0) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
        }
        if (!challenge.getCreatorId().equals(userId)) {
            String role = (String) StpUtil.getSession().get("role");
            if (!"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        if (req.getName() != null) challenge.setName(req.getName());
        if (req.getDescription() != null) challenge.setDescription(req.getDescription());
        if (req.getStartDate() != null) challenge.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) challenge.setEndDate(req.getEndDate());
        if (req.getRule() != null) challenge.setRule(req.getRule());

        challengeMapper.updateById(challenge);
        return getById(challengeId);
    }

    @Transactional
    public CheckinRecordVO checkin(Long challengeId, Long userId, CreateCheckinRecordRequest req) {
        CheckinChallenge challenge = challengeMapper.selectById(challengeId);
        if (challenge == null || challenge.getStatus() == 0) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
        }

        LocalDate today = LocalDate.now();
        if (today.isBefore(challenge.getStartDate()) || today.isAfter(challenge.getEndDate())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "挑战未开始或已结束");
        }

        // 检查今日是否已打卡
        CheckinRecord existing = recordMapper.selectOne(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getChallengeId, challengeId)
                .eq(CheckinRecord::getUserId, userId)
                .eq(CheckinRecord::getCheckinDate, today));
        if (existing != null) {
            throw new BusinessException(ErrorCode.ALREADY_CHECKED_IN);
        }

        // 判断是否首次打卡（用于 member_count）
        boolean isFirst = !recordMapper.exists(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getChallengeId, challengeId)
                .eq(CheckinRecord::getUserId, userId));

        CheckinRecord record = new CheckinRecord();
        record.setChallengeId(challengeId);
        record.setUserId(userId);
        record.setCheckinDate(today);
        record.setContent(req.getContent());
        if (req.getImageUrls() != null && !req.getImageUrls().isEmpty()) {
            try {
                record.setImageUrls(objectMapper.writeValueAsString(req.getImageUrls()));
            } catch (JsonProcessingException e) {
                record.setImageUrls("[]");
            }
        }
        record.setAiCheck(0);

        recordMapper.insert(record);

        // 首次打卡增加成员数
        if (isFirst) {
            challenge.setMemberCount(challenge.getMemberCount() + 1);
            challengeMapper.updateById(challenge);
        }

        log.info("User {} checked in to challenge {}", userId, challengeId);
        return toRecordVO(record, userId);
    }

    public List<CheckinRecordVO> getRecords(Long challengeId, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<CheckinRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(CheckinRecord::getChallengeId, challengeId);
        if (cursor != null) {
            qw.lt(CheckinRecord::getId, cursor);
        }
        qw.orderByDesc(CheckinRecord::getId);
        qw.last("LIMIT " + size);

        return recordMapper.selectList(qw).stream()
                .map(r -> toRecordVO(r, r.getUserId()))
                .toList();
    }

    public List<LeaderboardEntry> getLeaderboard(Long challengeId) {
        List<CheckinRecord> records = recordMapper.selectList(new LambdaQueryWrapper<CheckinRecord>()
                .eq(CheckinRecord::getChallengeId, challengeId));

        // 按 user 分组
        Map<Long, List<CheckinRecord>> grouped = records.stream()
                .collect(Collectors.groupingBy(CheckinRecord::getUserId));

        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Map.Entry<Long, List<CheckinRecord>> e : grouped.entrySet()) {
            Long uid = e.getKey();
            List<CheckinRecord> userRecords = e.getValue();
            int total = userRecords.size();
            int streak = calcStreak(userRecords);

            User user = userMapper.selectById(uid);
            entries.add(LeaderboardEntry.builder()
                    .userId(uid)
                    .userName(user != null ? user.getNickname() : "未知")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .totalDays(total)
                    .currentStreak(streak)
                    .build());
        }

        entries.sort((a, b) -> {
            int cmp = b.getTotalDays().compareTo(a.getTotalDays());
            if (cmp != 0) return cmp;
            return b.getCurrentStreak().compareTo(a.getCurrentStreak());
        });

        return entries;
    }

    @Transactional
    public void delete(Long challengeId, Long userId) {
        CheckinChallenge challenge = challengeMapper.selectById(challengeId);
        if (challenge == null || challenge.getStatus() == 0) {
            throw new BusinessException(ErrorCode.CHALLENGE_NOT_FOUND);
        }
        if (!challenge.getCreatorId().equals(userId)) {
            String role = (String) StpUtil.getSession().get("role");
            if (!"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        challenge.setStatus(0);
        challengeMapper.updateById(challenge);
        log.info("Checkin challenge deleted: id={}", challengeId);
    }

    private int calcStreak(List<CheckinRecord> records) {
        if (records.isEmpty()) return 0;

        List<LocalDate> dates = records.stream()
                .map(CheckinRecord::getCheckinDate)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();

        LocalDate today = LocalDate.now();
        // 连续打卡必须包含今天或昨天
        LocalDate latest = dates.get(0);
        if (!latest.equals(today) && !latest.equals(today.minusDays(1))) {
            return 0;
        }

        int streak = 1;
        for (int i = 0; i < dates.size() - 1; i++) {
            long gap = ChronoUnit.DAYS.between(dates.get(i + 1), dates.get(i));
            if (gap == 1) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    private CheckinChallengeVO toVO(CheckinChallenge c, Long currentUserId, boolean isMember, int totalDays, int streak) {
        User creator = userMapper.selectById(c.getCreatorId());
        UserVO creatorVO = null;
        if (creator != null) {
            creatorVO = UserVO.builder()
                    .id(creator.getId())
                    .nickname(creator.getNickname())
                    .avatarUrl(creator.getAvatarUrl())
                    .build();
        }

        return CheckinChallengeVO.builder()
                .id(c.getId())
                .spaceId(c.getSpaceId())
                .creatorId(c.getCreatorId())
                .creator(creatorVO)
                .name(c.getName())
                .description(c.getDescription())
                .startDate(c.getStartDate())
                .endDate(c.getEndDate())
                .rule(c.getRule())
                .memberCount(c.getMemberCount())
                .status(c.getStatus())
                .isMember(isMember)
                .myTotalDays(totalDays)
                .myConsecutiveDays(streak)
                .createdAt(c.getCreatedAt())
                .build();
    }

    private CheckinRecordVO toRecordVO(CheckinRecord r, Long userId) {
        User user = userMapper.selectById(userId);
        UserVO userVO = null;
        if (user != null) {
            userVO = UserVO.builder()
                    .id(user.getId())
                    .nickname(user.getNickname())
                    .avatarUrl(user.getAvatarUrl())
                    .build();
        }

        List<String> urls = Collections.emptyList();
        if (r.getImageUrls() != null && !r.getImageUrls().isEmpty() && !"[]".equals(r.getImageUrls())) {
            try {
                urls = objectMapper.readValue(r.getImageUrls(), List.class);
            } catch (Exception ignored) {
            }
        }

        return CheckinRecordVO.builder()
                .id(r.getId())
                .challengeId(r.getChallengeId())
                .userId(userId)
                .user(userVO)
                .checkinDate(r.getCheckinDate())
                .content(r.getContent())
                .imageUrls(urls)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
