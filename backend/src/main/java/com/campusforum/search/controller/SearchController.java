package com.campusforum.search.controller;

import com.campusforum.common.R;
import com.campusforum.search.dto.SearchResultVO;
import com.campusforum.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public R<List<SearchResultVO>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return R.ok(searchService.search(keyword, type, sort, cursor, limit));
    }
}
