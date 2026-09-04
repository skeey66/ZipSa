package com.zipsa.community;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    @EntityGraph(attributePaths = "user")
    List<Comment> findByPostIdAndStatusOrderByCreatedAtAsc(Long postId, ContentStatus status);

    int countByPostIdAndStatus(Long postId, ContentStatus status);
}
