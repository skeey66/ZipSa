package com.zipsa.community.dto;

import com.zipsa.community.Post;
import java.time.LocalDateTime;

/** 오퍼레이션 21 — 게시글 목록 (08 화면) */
public record PostSummaryResponse(
        Long id, String category, String title, String nickname,
        // 대출 결과를 등록한 회원에게 붙는 은행 뱃지 코드(KB/WOORI/NH/HANA).
        // "실제로 받아본 사람" 이라는 신호라 커뮤니티에서 글의 무게가 달라진다.
        java.util.List<String> badges,
        int viewCount, int likeCount, int commentCount, LocalDateTime createdAt
) {
    public static PostSummaryResponse from(Post p, java.util.List<String> badges) {
        return new PostSummaryResponse(p.getId(), p.getCategory().name(), p.getTitle(),
                p.getUser().getNickname(), badges, p.getViewCount(), p.getLikeCount(),
                p.getCommentCount(), p.getCreatedAt());
    }
}
