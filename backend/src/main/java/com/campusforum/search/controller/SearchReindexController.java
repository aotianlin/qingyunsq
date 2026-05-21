package com.campusforum.search.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campusforum.common.R;
import com.campusforum.post.domain.Post;
import com.campusforum.post.mapper.PostMapper;
import com.campusforum.resource.domain.Resource;
import com.campusforum.resource.mapper.ResourceMapper;
import com.campusforum.search.service.MeiliSearchClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/search")
@RequiredArgsConstructor
public class SearchReindexController {

    private final PostMapper postMapper;
    private final ResourceMapper resourceMapper;
    private final MeiliSearchClient meiliSearchClient;

    @PostMapping("/reindex")
    @SaCheckPermission("tenant:dashboard")
    public R<Map<String, Integer>> reindex() {
        int totalIndexed = 0;

        // 重建帖子索引
        Long lastId = null;
        while (true) {
            LambdaQueryWrapper<Post> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Post::getId, lastId);
            qw.eq(Post::getDeleted, 0).orderByAsc(Post::getId).last("LIMIT 500");
            List<Post> batch = postMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Post p : batch) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("id", p.getId());
                doc.put("title", p.getTitle());
                doc.put("content", p.getContent());
                doc.put("authorId", p.getAuthorId());
                doc.put("scope", p.getScope());
                doc.put("type", p.getType());
                meiliSearchClient.indexDocument("posts", doc);
                totalIndexed++;
            }
            lastId = batch.get(batch.size() - 1).getId();
        }

        // 重建资源索引
        lastId = null;
        while (true) {
            LambdaQueryWrapper<Resource> qw = new LambdaQueryWrapper<>();
            if (lastId != null) qw.gt(Resource::getId, lastId);
            qw.eq(Resource::getDeleted, 0).orderByAsc(Resource::getId).last("LIMIT 500");
            List<Resource> batch = resourceMapper.selectList(qw);
            if (batch.isEmpty()) break;
            for (Resource r : batch) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("id", r.getId());
                doc.put("fileName", r.getFileName());
                doc.put("description", r.getDescription());
                doc.put("fileType", r.getFileType());
                meiliSearchClient.indexDocument("resources", doc);
                totalIndexed++;
            }
            lastId = batch.get(batch.size() - 1).getId();
        }

        log.info("Full reindex completed: {} documents indexed", totalIndexed);
        return R.ok(Map.of("indexed", totalIndexed));
    }
}
