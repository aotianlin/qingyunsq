package com.campusforum.sensitive.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campusforum.common.R;
import com.campusforum.sensitive.domain.SensitiveWord;
import com.campusforum.sensitive.service.SensitiveWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/sensitive-words")
@RequiredArgsConstructor
public class SensitiveWordController {

    private final SensitiveWordService sensitiveWordService;

    @GetMapping
    @SaCheckPermission("tenant:sensitive:manage")
    public R<List<SensitiveWord>> list() {
        return R.ok(sensitiveWordService.listAll());
    }

    @PostMapping
    @SaCheckPermission("tenant:sensitive:manage")
    public R<Void> add(@RequestBody Map<String, Object> body) {
        String word = (String) body.get("word");
        int level = body.get("level") != null ? Integer.parseInt(body.get("level").toString()) : 1;
        // isRegex 可选，默认 false（普通词条）；显式传 true 时按正则录入，service 会校验正则合法性。
        boolean isRegex = parseBoolean(body.get("isRegex"));
        sensitiveWordService.add(word, level, isRegex);
        return R.ok();
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        return value != null && Boolean.parseBoolean(value.toString());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission("tenant:sensitive:manage")
    public R<Void> delete(@PathVariable Long id) {
        sensitiveWordService.delete(id);
        return R.ok();
    }
}
