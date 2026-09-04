package com.zipsa.community;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 중복 좋아요는 DB unique 제약(post_id, user_id)으로 막는다. */
@Entity
@Table(name = "post_likes")
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected PostLike() {
    }

    public PostLike(Long postId, Long userId) {
        this.postId = postId;
        this.userId = userId;
    }
}
