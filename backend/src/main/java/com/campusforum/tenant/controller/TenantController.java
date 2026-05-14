package com.campusforum.tenant.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campusforum.common.R;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @SaCheckPermission("super:tenant:manage")
    public R<List<Tenant>> list(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) Long cursor,
                                 @RequestParam(defaultValue = "20") int limit) {
        return R.ok(tenantService.listAll(keyword, cursor, limit));
    }

    @PostMapping
    @SaCheckPermission("super:tenant:manage")
    public R<Tenant> create(@RequestBody Map<String, String> body) {
        Tenant tenant = tenantService.create(
                body.get("code"),
                body.get("name"),
                body.get("domain"));
        return R.ok(tenant);
    }

    @PutMapping("/{id}")
    @SaCheckPermission("super:tenant:manage")
    public R<Tenant> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return R.ok(tenantService.update(id,
                body.get("name"),
                body.get("domain"),
                body.get("logoUrl")));
    }

    @PutMapping("/{id}/status")
    @SaCheckPermission("super:tenant:manage")
    public R<Void> toggleStatus(@PathVariable Long id) {
        tenantService.toggleStatus(id);
        return R.ok();
    }
}
