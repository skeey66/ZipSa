package com.zipsa.community.dto;

import com.zipsa.community.Post;
import java.time.LocalDateTime;
import java.util.List;

/** 오퍼레이션 22 — 게시글 상세 */
public record PostDetailResponse(
        Long id, String category, String title, String content, String nickname,
        List<String> badges,
        int viewCount, int likeCount, int commentCount, LocalDateTime createdAt,
        boolean liked, boolean mine, List<CommentResponse> comments
) {
    public static PostDetailResponse of(Post p, List<CommentResponse> comments,
                                        boolean liked, boolean mine, List<String> badges) {
        return new PostDetailResponse(p.getId(), p.getCategory().name(), p.getTitle(),
                p.getContent(), p.getUser().getNickname(), badges, p.getViewCount(), p.getLikeCount(),
                p.getCommentCount(), p.getCreatedAt(), liked, mine, comments);
    }
}
