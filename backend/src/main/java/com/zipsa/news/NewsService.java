package com.zipsa.news;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.news.dto.NewsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NewsService {

    private final NewsRepository newsRepository;

    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public Page<NewsResponse> getNews(String keyword, Pageable pageable) {
        Page<News> page = (keyword == null || keyword.isBlank())
                ? newsRepository.findAllByOrderByPublishedAtDescIdDesc(pageable)
                : newsRepository.search(keyword.trim(), pageable);
        return page.map(NewsResponse::listItem);
    }

    public NewsResponse getNews(Long newsId) {
        return newsRepository.findById(newsId)
                .map(NewsResponse::detail)
                .orElseThrow(() -> new BusinessException(ErrorCode.NEWS_NOT_FOUND));
    }
}
