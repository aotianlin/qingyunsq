package com.campusforum.achievement.controller;

import com.campusforum.achievement.dto.AchievementVO;
import com.campusforum.achievement.service.AchievementService;
import com.campusforum.common.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping
    public R<List<AchievementVO>> list(@RequestParam Long userId) {
        return R.ok(achievementService.getUserAchievements(userId));
    }
}
