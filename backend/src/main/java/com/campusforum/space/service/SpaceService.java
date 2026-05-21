package com.campusforum.space.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.notify.service.NotifyService;
import com.campusforum.space.domain.Space;
import com.campusforum.space.domain.SpaceMember;
import com.campusforum.space.dto.*;
import com.campusforum.space.mapper.SpaceMapper;
import com.campusforum.space.mapper.SpaceMemberMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceService {

    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;

    @Value("${space.max-join-count:20}")
    private int maxJoinCount;

    @Transactional
    public SpaceVO create(Long userId, CreateSpaceRequest req) {
        Space space = new Space();
        space.setOwnerId(userId);
        space.setName(req.getName());
        space.setDescription(req.getDescription());
        space.setCategory(req.getCategory());
        space.setVisibility(req.getVisibility());
        space.setMemberCount(1);
        space.setPostCount(0);
        space.setStatus(1);

        spaceMapper.insert(space);

        // owner 自动成为成员
        SpaceMember member = new SpaceMember();
        member.setSpaceId(space.getId());
        member.setUserId(userId);
        member.setRole("OWNER");
        member.setStatus(1);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.insert(member);

        log.info("Space created: id={}, name={}", space.getId(), space.getName());
        return toVO(space, userId, "OWNER", true);
    }

    public SpaceVO getById(Long spaceId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        String memberRole = null;
        boolean isMember = false;

        if (currentUserId != null) {
            SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                    .eq(SpaceMember::getSpaceId, spaceId)
                    .eq(SpaceMember::getUserId, currentUserId)
                    .eq(SpaceMember::getStatus, 1));
            if (member != null) {
                memberRole = member.getRole();
                isMember = true;
            }
        }

        return toVO(space, currentUserId, memberRole, isMember);
    }

    public List<SpaceVO> list(String category, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Space> qw = new LambdaQueryWrapper<>();
        qw.eq(Space::getStatus, 1);
        if (category != null && !category.isBlank()) {
            qw.eq(Space::getCategory, category);
        }
        if (cursor != null) {
            qw.lt(Space::getId, cursor);
        }
        qw.orderByDesc(Space::getMemberCount, Space::getId);
        qw.last("LIMIT " + size);

        List<Space> spaces = spaceMapper.selectList(qw);
        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return spaces.stream().map(s -> toVO(s, currentUserId, null, false)).toList();
    }

    @Transactional
    public SpaceVO update(Long spaceId, Long userId, UpdateSpaceRequest req) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }

        checkOwnership(spaceId, userId, space);

        if (req.getName() != null) space.setName(req.getName());
        if (req.getDescription() != null) space.setDescription(req.getDescription());
        if (req.getVisibility() != null) space.setVisibility(req.getVisibility());
        if (req.getSensitiveWords() != null) space.setSensitiveWords(req.getSensitiveWords());
        if (req.getPostNotice() != null) space.setPostNotice(req.getPostNotice());

        spaceMapper.updateById(space);
        return getById(spaceId);
    }

    @Transactional
    public SpaceVO join(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }

        // 检查是否已加入
        SpaceMember existing = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId));
        if (existing != null && existing.getStatus() == 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "已是该空间成员");
        }

        // 检查加入空间数量上限（仅新加入时，非重新申请）
        if (existing == null) {
            long joinedCount = memberMapper.selectCount(new LambdaQueryWrapper<SpaceMember>()
                    .eq(SpaceMember::getUserId, userId)
                    .eq(SpaceMember::getStatus, 1));
            if (joinedCount >= maxJoinCount) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                        "最多加入 " + maxJoinCount + " 个空间");
            }
        }

        int memberStatus = "PUBLIC".equals(space.getVisibility()) ? 1 : 0;

        if (existing != null) {
            existing.setStatus(memberStatus);
            existing.setJoinedAt(LocalDateTime.now());
            memberMapper.updateById(existing);
        } else {
            SpaceMember member = new SpaceMember();
            member.setSpaceId(spaceId);
            member.setUserId(userId);
            member.setRole("MEMBER");
            member.setStatus(memberStatus);
            member.setJoinedAt(LocalDateTime.now());
            memberMapper.insert(member);
        }

        // Bug fix 1.5: 原子更新成员数
        if (memberStatus == 1) {
            spaceMapper.incrementMemberCount(spaceId, 1);
        } else {
            // REVIEW 模式，通知空间主审核
            User applicant = userMapper.selectById(userId);
            String applicantName = applicant != null ? applicant.getNickname() : "有人";
            notifyService.create(space.getOwnerId(), userId, "JOIN",
                    "申请通知", applicantName + " 申请加入 " + space.getName(),
                    "/spaces/" + spaceId + "/members");
        }

        log.info("User {} joined space {}", userId, spaceId);
        Space refreshed = spaceMapper.selectById(spaceId);
        String role = "MEMBER";
        return toVO(refreshed, userId, role, memberStatus == 1);
    }

    @Transactional
    public void leave(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        if (space.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "群主不能退出，请转让群主或解散空间");
        }

        SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不是该空间成员");
        }

        member.setStatus(2); // 已退出
        memberMapper.updateById(member);

        // Bug fix 1.5: 原子更新成员数
        spaceMapper.incrementMemberCount(spaceId, -1);

        log.info("User {} left space {}", userId, spaceId);
    }

    public List<SpaceMemberVO> listMembers(Long spaceId, Long cursor, int limit) {
        int size = Math.min(limit, 100);
        LambdaQueryWrapper<SpaceMember> qw = new LambdaQueryWrapper<>();
        qw.eq(SpaceMember::getSpaceId, spaceId);
        qw.eq(SpaceMember::getStatus, 1);
        if (cursor != null) {
            qw.gt(SpaceMember::getId, cursor);
        }
        qw.orderByAsc(SpaceMember::getId);
        qw.last("LIMIT " + size);

        return memberMapper.selectList(qw).stream().map(m -> {
            User user = userMapper.selectById(m.getUserId());
            return SpaceMemberVO.builder()
                    .id(m.getId())
                    .spaceId(m.getSpaceId())
                    .userId(m.getUserId())
                    .user(user != null ? UserVO.builder()
                            .id(user.getId())
                            .nickname(user.getNickname())
                            .avatarUrl(user.getAvatarUrl())
                            .build() : null)
                    .role(m.getRole())
                    .status(m.getStatus())
                    .joinedAt(m.getJoinedAt())
                    .build();
        }).toList();
    }

    @Transactional
    public void approveMember(Long spaceId, Long operatorId, Long targetUserId) {
        checkOwnership(spaceId, operatorId, null);

        SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, targetUserId)
                .eq(SpaceMember::getStatus, 0));
        if (member == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无待审核申请");
        }

        member.setStatus(1);
        member.setJoinedAt(LocalDateTime.now());
        memberMapper.updateById(member);

        // Bug fix 1.5: 原子更新成员数
        spaceMapper.incrementMemberCount(spaceId, 1);
        log.info("Space {} member {} approved", spaceId, targetUserId);
    }

    @Transactional
    public void removeMember(Long spaceId, Long operatorId, Long targetUserId) {
        checkOwnership(spaceId, operatorId, null);

        SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, targetUserId)
                .eq(SpaceMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该用户不是空间成员");
        }
        if ("OWNER".equals(member.getRole())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "不能移除空间主");
        }

        member.setStatus(3); // 已拒绝/踢出
        memberMapper.updateById(member);

        // Bug fix 1.5: 原子更新成员数
        spaceMapper.incrementMemberCount(spaceId, -1);
    }

    @Transactional
    public void dismiss(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }

        String role = (String) StpUtil.getSession().get("role");
        if (!space.getOwnerId().equals(userId) && !"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        spaceMapper.deleteById(spaceId);
        log.info("Space dismissed: id={}", spaceId);
    }

    @Transactional
    public void setStatus(Long spaceId, Integer status) {
        Space space = spaceMapper.selectById(spaceId);
        if (space != null) {
            space.setStatus(status);
            spaceMapper.updateById(space);
            log.info("Space status changed: id={}, status={}", spaceId, status);
        }
    }

    public List<SpaceVO> listSpacesForAdmin(String keyword, String category, Integer status, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Space> qw = new LambdaQueryWrapper<>();
        if (cursor != null) {
            qw.lt(Space::getId, cursor);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.like(Space::getName, keyword);
        }
        if (category != null && !category.isBlank()) {
            qw.eq(Space::getCategory, category);
        }
        if (status != null) {
            qw.eq(Space::getStatus, status);
        }
        qw.orderByDesc(Space::getId);
        qw.last("LIMIT " + size);

        Long currentUserId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        return spaceMapper.selectList(qw).stream()
                .map(s -> toVO(s, currentUserId, null, false))
                .toList();
    }

    public void checkSpaceAdmin(Long spaceId, Long userId) {
        SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getStatus, 1));
        boolean isAdmin = member != null &&
                ("OWNER".equals(member.getRole()) || "ADMIN".equals(member.getRole()));
        if (!isAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    // Bug fix 1.6: 私有空间成员访问校验
    public void checkMemberAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        if ("PRIVATE".equals(space.getVisibility())) {
            if (userId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "私有空间需登录访问");
            }
            SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                    .eq(SpaceMember::getSpaceId, spaceId)
                    .eq(SpaceMember::getUserId, userId)
                    .eq(SpaceMember::getStatus, 1));
            if (member == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "非空间成员，无法查看帖子");
            }
        }
    }

    private void checkOwnership(Long spaceId, Long userId, Space space) {
        Space s = space != null ? space : spaceMapper.selectById(spaceId);
        // Bug fix 1.18: 显式空值检查
        if (s == null) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        SpaceMember member = memberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getStatus, 1));
        boolean isOwnerOrAdmin = member != null &&
                ("OWNER".equals(member.getRole()) || "ADMIN".equals(member.getRole()));
        if (!s.getOwnerId().equals(userId) && !isOwnerOrAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private SpaceVO toVO(Space space, Long currentUserId, String memberRole, boolean isMember) {
        User owner = userMapper.selectById(space.getOwnerId());
        UserVO ownerVO = null;
        if (owner != null) {
            ownerVO = UserVO.builder()
                    .id(owner.getId())
                    .nickname(owner.getNickname())
                    .avatarUrl(owner.getAvatarUrl())
                    .build();
        }

        return SpaceVO.builder()
                .id(space.getId())
                .ownerId(space.getOwnerId())
                .owner(ownerVO)
                .name(space.getName())
                .description(space.getDescription())
                .category(space.getCategory())
                .visibility(space.getVisibility())
                .memberCount(space.getMemberCount())
                .postCount(space.getPostCount())
                .status(space.getStatus())
                .isMember(isMember)
                .memberRole(memberRole)
                .sensitiveWords(space.getSensitiveWords())
                .postNotice(space.getPostNotice())
                .createdAt(space.getCreatedAt())
                .build();
    }
}
