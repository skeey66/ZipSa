package com.zipsa.community.dto;

import com.zipsa.community.Comment;
import java.time.LocalDateTime;

public record CommentResponse(Long id, String nickname, String content,
                              LocalDateTime createdAt, boolean mine) {
    public static CommentResponse from(Comment c, Long viewerId) {
        return new CommentResponse(c.getId(), c.getUser().getNickname(), c.getContent(),
                c.getCreatedAt(), viewerId != null && c.isOwnedBy(viewerId));
    }
}
