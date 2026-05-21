package com.campusforum.report.controller;

import com.campusforum.common.R;
import com.campusforum.report.dto.CreateReportRequest;
import com.campusforum.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public R<Void> create(@Valid @RequestBody CreateReportRequest req) {
        reportService.create(req.getTargetType(), req.getTargetId(), req.getReason(), req.getDescription());
        return R.ok();
    }
}
