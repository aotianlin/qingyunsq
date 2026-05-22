package com.campusforum.resource.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.BusinessException;
import com.campusforum.common.ErrorCode;
import com.campusforum.infra.StorageService;
import com.campusforum.resource.domain.Resource;
import com.campusforum.resource.dto.ResourcePreviewVO;
import com.campusforum.resource.dto.ResourceVO;
import com.campusforum.resource.dto.UploadResourceRequest;
import com.campusforum.resource.mapper.ResourceMapper;
import com.campusforum.space.domain.Space;
import com.campusforum.space.domain.SpaceMember;
import com.campusforum.space.mapper.SpaceMapper;
import com.campusforum.space.mapper.SpaceMemberMapper;
import com.campusforum.user.domain.User;
import com.campusforum.user.dto.UserVO;
import com.campusforum.user.mapper.UserMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Slf4j
@Service
public class ResourceService {

    private final ResourceMapper resourceMapper;
    private final UserMapper userMapper;
    private final SpaceMapper spaceMapper;
    private final SpaceMemberMapper spaceMemberMapper;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    // SEC-03: 从配置文件读取文件扩展名白名单
    private final Set<String> allowedExtensions;

    public ResourceService(ResourceMapper resourceMapper, UserMapper userMapper,
                           SpaceMapper spaceMapper, SpaceMemberMapper spaceMemberMapper,
                           StorageService storageService, ObjectMapper objectMapper,
                           @Value("${upload.allowed-extensions:pdf,doc,docx,ppt,pptx,xls,xlsx,zip,rar,7z,jpg,jpeg,png,gif,webp,md,markdown}") String allowedExtensionsConfig) {
        this.resourceMapper = resourceMapper;
        this.userMapper = userMapper;
        this.spaceMapper = spaceMapper;
        this.spaceMemberMapper = spaceMemberMapper;
        this.storageService = storageService;
        this.objectMapper = objectMapper;
        this.allowedExtensions = Arrays.stream(allowedExtensionsConfig.split(","))
                .map(String::trim).map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public ResourceVO upload(Long userId, MultipartFile file, UploadResourceRequest req) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "文件名为空");
        }

        // SEC-03: 校验文件扩展名白名单，防止上传可执行/危险文件
        int dot = originalName.lastIndexOf('.');
        String ext = dot >= 0 ? originalName.substring(dot + 1).toLowerCase() : "";
        if (ext.isBlank() || !allowedExtensions.contains(ext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(),
                    "不支持的文件类型：." + ext + "，允许的类型：" + String.join(", ", allowedExtensions));
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }

        // MD5 去重
        String md5 = md5Hex(fileBytes);
        Resource existing = resourceMapper.selectOne(new LambdaQueryWrapper<Resource>()
                .eq(Resource::getFileMd5, md5)
                .eq(Resource::getStatus, 1)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("Duplicate file detected by MD5, reusing existing resource {}", existing.getId());
            return toVO(existing);
        }

        String storageKey;
        storageKey = storageService.upload(new ByteArrayInputStream(fileBytes), originalName, file.getContentType());

        Resource resource = new Resource();
        resource.setUploaderId(userId);
        resource.setSpaceId(req.getSpaceId());
        resource.setFileName(originalName);
        resource.setFileSize(file.getSize());
        resource.setFileType(ext);
        resource.setStorageKey(storageKey);
        resource.setFileMd5(md5);
        resource.setVisibility(req.getVisibility() != null ? req.getVisibility() : "PUBLIC");
        resource.setCollege(req.getCollege());
        resource.setMajor(req.getMajor());
        resource.setCourse(req.getCourse());
        resource.setSemester(req.getSemester());
        if (req.getTags() != null && !req.getTags().isEmpty()) {
            try {
                resource.setTags(objectMapper.writeValueAsString(req.getTags()));
            } catch (JsonProcessingException e) {
                resource.setTags("[]");
            }
        }
        resource.setDescription(req.getDescription());
        resource.setDownloadCount(0);
        resource.setCollectCount(0);
        resource.setStatus(1);

        resourceMapper.insert(resource);
        log.info("Resource uploaded: id={}, fileName={}", resource.getId(), originalName);
        return toVO(resource);
    }

    public ResourceVO getById(Long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || resource.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ensureCanAccess(resource);
        return toVO(resource);
    }

    public List<ResourceVO> list(Long spaceId, String college, String major, String course, Long cursor, int limit) {
        int size = Math.min(limit, 50);
        LambdaQueryWrapper<Resource> qw = new LambdaQueryWrapper<>();
        qw.eq(Resource::getStatus, 1);
        if (spaceId != null) {
            qw.eq(Resource::getSpaceId, spaceId);
        }
        if (college != null && !college.isBlank()) {
            qw.eq(Resource::getCollege, college);
        }
        if (major != null && !major.isBlank()) {
            qw.eq(Resource::getMajor, major);
        }
        if (course != null && !course.isBlank()) {
            qw.like(Resource::getCourse, course);
        }
        if (cursor != null) {
            qw.lt(Resource::getId, cursor);
        }
        qw.orderByDesc(Resource::getId);
        qw.last("LIMIT " + size);

        // 列表层面也按可见性过滤，避免 PRIVATE / 仅空间成员可见的资源在列表中泄漏
        Long currentUserId = currentUserIdOrNull();
        String currentRole = currentRoleOrNull();
        return resourceMapper.selectList(qw).stream()
                .filter(r -> canAccess(r, currentUserId, currentRole))
                .map(this::toVO)
                .toList();
    }

    @Transactional
    public InputStream download(Long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || resource.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        ensureCanAccess(resource);

        // 用 SQL 原子自增计数，避免并发下载时的丢失更新
        resourceMapper.incrementDownloadCount(resourceId);

        return storageService.download(resource.getStorageKey());
    }

    public InputStream preview(Long resourceId) {
        Resource resource = getActiveResource(resourceId);
        ensureCanAccess(resource);
        return storageService.download(resource.getStorageKey());
    }

    public ResourcePreviewVO previewText(Long resourceId) {
        Resource resource = getActiveResource(resourceId);
        ensureCanAccess(resource);
        String fileType = resource.getFileType() == null ? "" : resource.getFileType().toLowerCase();

        try (InputStream is = storageService.download(resource.getStorageKey())) {
            String content;
            if ("md".equals(fileType) || "markdown".equals(fileType)) {
                content = readUtf8Text(is);
            } else if ("docx".equals(fileType)) {
                content = extractDocxText(is);
            } else {
                throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "该文件类型暂不支持文本预览");
            }

            return ResourcePreviewVO.builder()
                    .id(resource.getId())
                    .fileName(resource.getFileName())
                    .fileType(resource.getFileType())
                    .content(content)
                    .build();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR);
        }
    }

    public String getFileName(Long resourceId) {
        Resource resource = getActiveResource(resourceId);
        ensureCanAccess(resource);
        return resource.getFileName();
    }

    public String getFileType(Long resourceId) {
        Resource resource = getActiveResource(resourceId);
        ensureCanAccess(resource);
        return resource.getFileType();
    }

    @Transactional
    public void delete(Long resourceId, Long userId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || resource.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!resource.getUploaderId().equals(userId)) {
            String role = (String) StpUtil.getSession().get("role");
            if (!"TENANT_ADMIN".equals(role) && !"SUPER_ADMIN".equals(role)) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        storageService.delete(resource.getStorageKey());
        resourceMapper.deleteById(resourceId);
        log.info("Resource deleted: id={}", resourceId);
    }

    private ResourceVO toVO(Resource r) {
        User uploader = userMapper.selectById(r.getUploaderId());
        UserVO uploaderVO = null;
        if (uploader != null) {
            uploaderVO = UserVO.builder()
                    .id(uploader.getId())
                    .nickname(uploader.getNickname())
                    .avatarUrl(uploader.getAvatarUrl())
                    .build();
        }

        List<String> tagList = Collections.emptyList();
        if (r.getTags() != null && !r.getTags().isEmpty() && !"[]".equals(r.getTags())) {
            try {
                tagList = objectMapper.readValue(r.getTags(), List.class);
            } catch (Exception ignored) {
            }
        }

        return ResourceVO.builder()
                .id(r.getId())
                .uploaderId(r.getUploaderId())
                .uploader(uploaderVO)
                .spaceId(r.getSpaceId())
                .fileName(r.getFileName())
                .fileSize(r.getFileSize())
                .fileType(r.getFileType())
                .visibility(r.getVisibility())
                .college(r.getCollege())
                .major(r.getMajor())
                .course(r.getCourse())
                .semester(r.getSemester())
                .tags(tagList)
                .downloadCount(r.getDownloadCount())
                .collectCount(r.getCollectCount())
                .version(r.getVersion())
                .description(r.getDescription())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private Resource getActiveResource(Long resourceId) {
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null || resource.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return resource;
    }

    /**
     * 资源访问可见性校验。规则：
     * <ul>
     *   <li>上传者本人或租户/超管：始终可访问；</li>
     *   <li>visibility = PUBLIC：登录用户均可访问；</li>
     *   <li>visibility = PRIVATE：仅上传者可访问（管理员除外）；</li>
     *   <li>visibility = SPACE 且资源属于某个空间：仅该空间成员可访问；</li>
     *   <li>其他未知值按 PRIVATE 处理。</li>
     * </ul>
     */
    private void ensureCanAccess(Resource resource) {
        Long currentUserId = currentUserIdOrNull();
        String role = currentRoleOrNull();
        if (!canAccess(resource, currentUserId, role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canAccess(Resource resource, Long currentUserId, String role) {
        if ("TENANT_ADMIN".equals(role) || "SUPER_ADMIN".equals(role)) {
            return true;
        }
        if (currentUserId != null && currentUserId.equals(resource.getUploaderId())) {
            return true;
        }
        String visibility = resource.getVisibility() == null ? "PUBLIC" : resource.getVisibility().toUpperCase();
        return switch (visibility) {
            case "PUBLIC" -> currentUserId != null;
            case "SPACE" -> currentUserId != null && resource.getSpaceId() != null
                    && isSpaceMember(resource.getSpaceId(), currentUserId);
            // PRIVATE 或未知值：默认仅上传者本人，已在前面 return 过
            default -> false;
        };
    }

    private boolean isSpaceMember(Long spaceId, Long userId) {
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || space.getDeleted() == 1) {
            return false;
        }
        // 空间所有者一定是成员
        if (userId.equals(space.getOwnerId())) return true;
        SpaceMember member = spaceMemberMapper.selectOne(new LambdaQueryWrapper<SpaceMember>()
                .eq(SpaceMember::getSpaceId, spaceId)
                .eq(SpaceMember::getUserId, userId)
                .eq(SpaceMember::getStatus, 1)
                .last("LIMIT 1"));
        return member != null;
    }

    private static Long currentUserIdOrNull() {
        try {
            return StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String currentRoleOrNull() {
        try {
            if (!StpUtil.isLogin()) return null;
            return (String) StpUtil.getSession().get("role");
        } catch (Exception e) {
            return null;
        }
    }

    private static String readUtf8Text(InputStream is) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        is.transferTo(out);
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String extractDocxText(InputStream is) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    byte[] xml = zip.readAllBytes();
                    return parseDocxDocumentXml(xml);
                }
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "DOCX 文件内容不完整，无法预览");
    }

    private static String parseDocxDocumentXml(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // 防 XXE：禁用 DOCTYPE 与外部实体加载，避免上传文档触发 SSRF/任意文件读取
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            NodeList paragraphs = document.getElementsByTagNameNS("*", "p");
            StringBuilder text = new StringBuilder();

            // DOCX 正文按段落和文本节点存储，预览时保留段落换行，避免直接拼接成一整行。
            for (int i = 0; i < paragraphs.getLength(); i++) {
                NodeList nodes = paragraphs.item(i).getChildNodes();
                appendDocxTextNodes(nodes, text);
                text.append('\n');
            }
            return text.toString().trim();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "DOCX 文件解析失败，无法预览");
        }
    }

    private static void appendDocxTextNodes(NodeList nodes, StringBuilder text) {
        for (int i = 0; i < nodes.getLength(); i++) {
            String localName = nodes.item(i).getLocalName();
            if ("t".equals(localName)) {
                text.append(nodes.item(i).getTextContent());
            } else if ("tab".equals(localName)) {
                text.append('\t');
            } else if ("br".equals(localName)) {
                text.append('\n');
            }
            appendDocxTextNodes(nodes.item(i).getChildNodes(), text);
        }
    }

    private static String md5Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
