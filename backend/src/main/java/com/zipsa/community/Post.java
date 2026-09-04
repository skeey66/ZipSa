package com.zipsa.community;

import com.zipsa.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 목록에서 작성자 닉네임을 보여줘야 하는데, 게시글마다 따로 조회하면 N+1 이 난다.
    // 조회 쪽에서 fetch join 으로 한 번에 가져온다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostCategory category;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status = ContentStatus.PUBLISHED;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Post() {
    }

    public static Post write(User user, String title, String content, PostCategory category) {
        Post p = new Post();
        p.user = user;
        p.title = title;
        p.content = content;
        p.category = category;
        return p;
    }

    public void edit(String title, String content, PostCategory category) {
        if (title != null) this.title = title;
        if (content != null) this.content = content;
        if (category != null) this.category = category;
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete() {
        this.status = ContentStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void increaseView() { this.viewCount++; }
    public void applyCounts(int likes, int comments) {
        this.likeCount = likes;
        this.commentCount = comments;
    }

    public boolean isOwnedBy(Long userId) { return user.getId().equals(userId); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public PostCategory getCategory() { return category; }
    public int getViewCount() { return viewCount; }
    public int getLikeCount() { return likeCount; }
    public int getCommentCount() { return commentCount; }
    public ContentStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
