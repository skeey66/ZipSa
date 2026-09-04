package com.zipsa.news;

import com.zipsa.common.ApiResponse;
import com.zipsa.news.dto.NewsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

/** 상단바 「정보」 하위 뉴스 화면. 전부 비로그인 조회 가능. */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService service;

    public NewsController(NewsService service) {
        this.service = service;
    }

    /** NEWS-001 — 뉴스 목록 */
    @GetMapping
    public ApiResponse<Page<NewsResponse>> getNews(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getNews(keyword, PageRequest.of(page, size)));
    }

    /** NEWS-002 — 뉴스 상세 */
    @GetMapping("/{newsId}")
    public ApiResponse<NewsResponse> getOne(@PathVariable Long newsId) {
        return ApiResponse.ok(service.getNews(newsId));
    }
}
