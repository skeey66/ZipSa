package com.zipsa.news.dto;

import com.zipsa.news.News;
import java.time.LocalDateTime;

/**
 * NEWS-001 목록 · NEWS-002 상세 공용.
 *
 * content 는 상세에서만 채웁니다. 목록에 본문을 실으면 20건만 해도 응답이 수백 KB 가 됩니다.
 */
public record NewsResponse(
        Long newsId,
        String title,
        String summary,
        String content,
        String pressName,
        String sourceUrl,
        String thumbnailUrl,
        LocalDateTime publishedAt
) {
    public static NewsResponse listItem(News n) {
        return of(n, false);
    }

    public static NewsResponse detail(News n) {
        return of(n, true);
    }

    private static NewsResponse of(News n, boolean full) {
        return new NewsResponse(n.getId(), n.getTitle(), n.getSummary(),
                full ? n.getContent() : null,
                n.getPressName(), n.getSourceUrl(), n.getThumbnailUrl(), n.getPublishedAt());
    }
}
