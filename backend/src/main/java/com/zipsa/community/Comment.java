package com.zipsa.community;

import com.zipsa.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContentStatus status = ContentStatus.PUBLISHED;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected Comment() {
    }

    public static Comment write(Post post, User user, String content) {
        Comment c = new Comment();
        c.post = post;
        c.user = user;
        c.content = content;
        return c;
    }

    public void softDelete() {
        this.status = ContentStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(Long userId) { return user.getId().equals(userId); }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getContent() { return content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
