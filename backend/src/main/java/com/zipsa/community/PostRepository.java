package com.zipsa.community;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

    // 목록에서 작성자 닉네임을 쓰므로 user 를 함께 가져온다. 없으면 게시글 수만큼 쿼리가 더 나간다.
    @EntityGraph(attributePaths = "user")
    Page<Post> findByStatusOrderByCreatedAtDescIdDesc(ContentStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Post> findByStatusAndCategoryOrderByCreatedAtDescIdDesc(
            ContentStatus status, PostCategory category, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Post> findByStatusAndTitleContainingOrderByCreatedAtDescIdDesc(
            ContentStatus status, String keyword, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<Post> findByIdAndStatus(Long id, ContentStatus status);

    /** 01 메인 인기글 — 좋아요·댓글이 많은 순. */
    @Query("""
            select p from Post p join fetch p.user
            where p.status = com.zipsa.community.ContentStatus.PUBLISHED
            order by (p.likeCount * 2 + p.commentCount) desc, p.createdAt desc
            """)
    Page<Post> findPopular(Pageable pageable);
}
