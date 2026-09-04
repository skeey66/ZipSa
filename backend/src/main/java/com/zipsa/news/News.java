package com.zipsa.news;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 뉴스 (명세 v4 §5).
 *
 * ⚠️ 기사 본문 컬럼이 없습니다. 본문은 언론사 저작물이라 저장하지 않고,
 *    제목·요약·원문 링크만 두고 읽기는 원문 사이트로 보냅니다.
 *    명세 5.2 의 content 필드를 뺀 이유입니다.
 */
@Entity
@Table(name = "news")
public class News {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String summary;

    /**
     * 기사 본문. 언론사 저작물이라 배포하지 않는 프로젝트 전제로만 저장한다.
     * 목록 응답에는 넣지 않는다 — 기사 100건이면 응답이 수백 KB 가 된다.
     */
    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "press_name", length = 100)
    private String pressName;

    @Column(name = "source_url", nullable = false, length = 1000)
    private String sourceUrl;

    @Column(name = "thumbnail_url", length = 1000)
    private String thumbnailUrl;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    protected News() {
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getContent() { return content; }
    public String getPressName() { return pressName; }
    public String getSourceUrl() { return sourceUrl; }
    public String getThumbnailUrl() { return thumbnailUrl; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
}
