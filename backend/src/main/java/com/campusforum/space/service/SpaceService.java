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
import com.campusforum.user.dto.PublicUserVO;
import com.campusforum.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpaceService {

    /** 公开空间：任何人可见、可直接加入（无需审核）。 */
    private static final String VISIBILITY_PUBLIC = "PUBLIC";

    /** 合法的空间可见性取值白名单（与 schema.sql 注释保持一致）。 */
    private static final Set<String> VALID_VISIBILITIES = Set.of("PUBLIC", "REVIEW", "INVITE");

    /** 合法的空间分类取值白名单。 */
    private static final Set<String> VALID_CATEGORIES = Set.of("MAJOR", "CLASS", "CLUB", "INTEREST");

    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper memberMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;

    @Value("${space.max-join-count:20}")
    private int maxJoinCount;

    @Transactional
    public SpaceVO create(Long userId, CreateSpaceRequest req) {
        // 校验分类与可见性取值合法（DTO 仅有 @NotBlank，未限定枚举）。
        // 尤其是 visibility：传入非法值会让空间永远落入审核分支且绕过 checkMemberAccess 的预期语义。
        String visibility = req.getVisibility() == null ? VISIBILITY_PUBLIC : req.getVisibility();
        if (!VALID_CATEGORIES.contains(req.getCategory())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的空间分类");
        }
        if (!VALID_VISIBILITIES.contains(visibility)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的可见性设置");
        }

        Space space = new Space();
        space.setOwnerId(userId);
        space.setName(req.getName());
        space.setDescription(req.getDescription());
        space.setCategory(req.getCategory());
        space.setVisibility(visibility);
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
        return toVOList(spaces, currentUserId);
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
        if (req.getVisibility() != null) {
            if (!VALID_VISIBILITIES.contains(req.getVisibility())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的可见性设置");
            }
            space.setVisibility(req.getVisibility());
        }
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

        int memberStatus = VISIBILITY_PUBLIC.equals(space.getVisibility()) ? 1 : 0;

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
            try {
                memberMapper.insert(member);
            } catch (DuplicateKeyException e) {
                // selectOne 预检查与 insert 之间存在并发窗口，最终由唯一索引
                // uk_space_user (space_id, user_id) 兜底。命中说明并发重复加入，
                // 转换为友好提示而非以 500 暴露底层异常。
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "已是该空间成员或申请处理中");
            }
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

        List<SpaceMember> members = memberMapper.selectList(qw);
        if (members.isEmpty()) {
            return List.of();
        }
        // 优化：一次性批量加载成员用户，避免循环内逐个 selectById 造成的 N+1 查询。
        List<Long> userIds = members.stream().map(SpaceMember::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return members.stream().map(m -> SpaceMemberVO.builder()
                .id(m.getId())
                .spaceId(m.getSpaceId())
                .userId(m.getUserId())
                .user(PublicUserVO.from(userMap.get(m.getUserId())))
                .role(m.getRole())
                .status(m.getStatus())
                .joinedAt(m.getJoinedAt())
                .build()).toList();
    }

    @Transactional
    public void approveMember(Long spaceId, Long operatorId, Long targetUserId) {
        // 显式校验空间存在且未被停用（status=1）。
        // 注：已解散空间走逻辑删除，selectById 已自动过滤；此处补 status=0 的停用场景，
        // 避免向已停用空间审批新成员。
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        if (space.getStatus() != null && space.getStatus() == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "空间已停用，无法审批成员");
        }
        checkOwnership(spaceId, operatorId, space);

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
        // 校验 status 取值合法（仅 0 停用 / 1 正常），避免管理员误传 null 或任意值写脏数据。
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "无效的状态值");
        }
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            // 原实现 if(space != null) 静默忽略不存在的空间，管理员得不到任何反馈。
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        space.setStatus(status);
        spaceMapper.updateById(space);
        log.info("Space status changed: id={}, status={}", spaceId, status);
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
        return toVOList(spaceMapper.selectList(qw), currentUserId);
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

    // Bug fix 1.6 + 安全修复：非公开空间（REVIEW/INVITE）的帖子仅对成员可见。
    // 历史 bug：此处曾判断 "PRIVATE".equals(visibility)，但系统中可见性取值只有
    // PUBLIC/REVIEW/INVITE，根本不存在 PRIVATE，导致该校验恒为 false 而完全失效——
    // 任何登录用户都能读取审核制/邀请制空间的全部帖子（越权读取）。
    // 现改为"非 PUBLIC 即需成员校验"，与 join() 的 memberStatus 语义对齐。
    public void checkMemberAccess(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.SPACE_NOT_FOUND);
        }
        if (!VISIBILITY_PUBLIC.equals(space.getVisibility())) {
            if (userId == null) {
                throw new BusinessException(ErrorCode.FORBIDDEN.getCode(), "该空间需登录访问");
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

    /**
     * 批量构建 SpaceVO 列表（用于列表类接口）。
     *
     * <p>优化：一次性 selectBatchIds 加载所有 owner，避免每个空间各查一次 owner 的 N+1 问题。
     * 列表场景下成员关系统一置为非成员视图（isMember=false / memberRole=null），
     * 与原 {@code list} / {@code listSpacesForAdmin} 行为保持一致。</p>
     */
    private List<SpaceVO> toVOList(List<Space> spaces, Long currentUserId) {
        if (spaces == null || spaces.isEmpty()) {
            return List.of();
        }
        List<Long> ownerIds = spaces.stream().map(Space::getOwnerId).distinct().toList();
        Map<Long, PublicUserVO> ownerMap = userMapper.selectBatchIds(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, PublicUserVO::from));
        return spaces.stream()
                .map(s -> buildVO(s, ownerMap.get(s.getOwnerId()), null, false))
                .toList();
    }

    private SpaceVO toVO(Space space, Long currentUserId, String memberRole, boolean isMember) {
        PublicUserVO ownerVO = PublicUserVO.from(userMapper.selectById(space.getOwnerId()));
        return buildVO(space, ownerVO, memberRole, isMember);
    }

    private SpaceVO buildVO(Space space, PublicUserVO ownerVO, String memberRole, boolean isMember) {
        // 安全：sensitiveWords 是空间的敏感词屏蔽配置（审核用），属于管理配置，
        // 不应对普通成员 / 非成员 / 匿名用户暴露——否则会帮助恶意用户规避内容过滤。
        // 仅 OWNER / ADMIN 视图返回该字段，其余视图置空。
        boolean isManager = "OWNER".equals(memberRole) || "ADMIN".equals(memberRole);
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
                .sensitiveWords(isManager ? space.getSensitiveWords() : null)
                .postNotice(space.getPostNotice())
                .createdAt(space.getCreatedAt())
                .build();
    }
}
