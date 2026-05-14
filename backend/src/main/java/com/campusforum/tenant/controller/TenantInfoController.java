package com.campusforum.tenant.controller;

import com.campusforum.common.R;
import com.campusforum.tenant.TenantContext;
import com.campusforum.tenant.domain.Tenant;
import com.campusforum.tenant.mapper.TenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TenantInfoController {

    private final TenantMapper tenantMapper;

    @GetMapping("/tenant/info")
    public R<Map<String, Object>> info() {
        Long tid = TenantContext.getTenantId();
        Tenant t = tenantMapper.selectById(tid != null ? tid : 1L);
        if (t == null) {
            return R.ok(Map.of("name", "CampusForum", "announcement", ""));
        }
        return R.ok(Map.of(
                "id", t.getId(),
                "code", t.getCode(),
                "name", t.getName(),
                "logoUrl", t.getLogoUrl() != null ? t.getLogoUrl() : "",
                "domain", t.getDomain() != null ? t.getDomain() : "",
                "announcement", t.getAnnouncement() != null ? t.getAnnouncement() : ""
        ));
    }
}
