package com.zipsa.community;

import com.zipsa.common.BusinessException;
import com.zipsa.common.ErrorCode;
import com.zipsa.community.dto.*;
import com.zipsa.user.User;
import com.zipsa.loan.BankCode;
import com.zipsa.loan.LoanActualRepository;
import com.zipsa.user.UserRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommunityService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final LoanActualRepository loanActualRepository;

    public CommunityService(PostRepository postRepository, CommentRepository commentRepository,
                            PostLikeRepository postLikeRepository, UserRepository userRepository,
                            LoanActualRepository loanActualRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.userRepository = userRepository;
        this.loanActualRepository = loanActualRepository;
    }

    /**
     * 게시글 작성자들의 은행 뱃지를 한 번에 가져온다.
     * 글마다 조회하면 20건 목록에 쿼리가 20번 더 나간다(N+1).
     */
    private Map<Long, List<String>> badgesOf(List<Post> posts) {
        List<Long> userIds = posts.stream().map(p -> p.getUser().getId()).distinct().toList();
        if (userIds.isEmpty()) return Map.of();

        Map<Long, List<String>> map = new HashMap<>();
        for (var row : loanActualRepository.findBanksOfUsers(userIds)) {
            BankCode.fromDisplayName(row.getBankName()).ifPresent(code ->
                    map.computeIfAbsent(row.getUserId(), k -> new java.util.ArrayList<>())
                            .add(code.name()));
        }
        return map;
    }

    /** 오퍼레이션 21 — 목록. category 와 keyword 는 둘 다 선택. */
    public Page<PostSummaryResponse> getPosts(PostCategory category, String keyword,
                                              Pageable pageable) {
        Page<Post> page;
        if (keyword != null && !keyword.isBlank()) {
            page = postRepository.findByStatusAndTitleContainingOrderByCreatedAtDescIdDesc(
                    ContentStatus.PUBLISHED, keyword.trim(), pageable);
        } else if (category != null) {
            page = postRepository.findByStatusAndCategoryOrderByCreatedAtDescIdDesc(
                    ContentStatus.PUBLISHED, category, pageable);
        } else {
            page = postRepository.findByStatusOrderByCreatedAtDescIdDesc(
                    ContentStatus.PUBLISHED, pageable);
        }
        Map<Long, List<String>> badges = badgesOf(page.getContent());
        return page.map(p -> PostSummaryResponse.from(p,
                badges.getOrDefault(p.getUser().getId(), List.of())));
    }

    /** 01 메인 인기글 */
    public List<PostSummaryResponse> getPopular(Pageable pageable) {
        var page = postRepository.findPopular(pageable);
        Map<Long, List<String>> badges = badgesOf(page.getContent());
        return page.map(p -> PostSummaryResponse.from(p,
                badges.getOrDefault(p.getUser().getId(), List.of()))).getContent();
    }

    /** 오퍼레이션 22 — 상세. 조회수를 올리므로 쓰기 트랜잭션이다. */
    @Transactional
    public PostDetailResponse getPost(Long postId, Long viewerId) {
        Post post = findPost(postId);
        post.increaseView();
        List<CommentResponse> comments =
                commentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(postId, ContentStatus.PUBLISHED)
                        .stream().map(c -> CommentResponse.from(c, viewerId)).toList();
        boolean liked = viewerId != null && postLikeRepository.existsByPostIdAndUserId(postId, viewerId);
        boolean mine = viewerId != null && post.isOwnedBy(viewerId);
        List<String> badges = badgesOf(List.of(post))
                .getOrDefault(post.getUser().getId(), List.of());
        return PostDetailResponse.of(post, comments, liked, mine, badges);
    }

    /** 오퍼레이션 23 — 작성 */
    @Transactional
    public Long writePost(Long userId, PostRequest request) {
        User user = findUser(userId);
        Post post = Post.write(user, request.title(), request.content(), request.category());
        return postRepository.save(post).getId();
    }

    /** 오퍼레이션 24 — 수정 */
    @Transactional
    public void editPost(Long userId, Long postId, PostRequest request) {
        Post post = findPost(postId);
        requireOwner(post, userId);
        post.edit(request.title(), request.content(), request.category());
    }

    /** 오퍼레이션 25 — 삭제 (soft delete) */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = findPost(postId);
        requireOwner(post, userId);
        post.softDelete();
    }

    /** 오퍼레이션 26 — 좋아요 토글 */
    @Transactional
    public boolean toggleLike(Long userId, Long postId) {
        Post post = findPost(postId);
        boolean liked;
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            liked = false;
        } else {
            try {
                postLikeRepository.save(new PostLike(postId, userId));
                liked = true;
            } catch (DataIntegrityViolationException e) {
                // 더블클릭 등으로 동시에 들어온 경우. unique 제약이 막아준 것이라 이미 눌린 상태다.
                liked = true;
            }
        }
        syncCounts(post);
        return liked;
    }

    /** 오퍼레이션 27 — 댓글 작성 */
    @Transactional
    public Long writeComment(Long userId, Long postId, CommentRequest request) {
        Post post = findPost(postId);
        Comment comment = commentRepository.save(Comment.write(post, findUser(userId), request.content()));
        syncCounts(post);
        return comment.getId();
    }

    /** 오퍼레이션 28 — 댓글 삭제 */
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
        if (!comment.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_POST_OWNER, "본인이 작성한 댓글만 삭제할 수 있습니다.");
        }
        comment.softDelete();
    }

    /**
     * 집계 컬럼을 실제 행 수로 다시 맞춥니다.
     * +1/-1 로 누적하면 동시 요청이나 롤백 때 화면 숫자와 DB 가 어긋나고,
     * 한 번 어긋나면 스스로 회복되지 않습니다.
     */
    private void syncCounts(Post post) {
        post.applyCounts(
                postLikeRepository.countByPostId(post.getId()),
                commentRepository.countByPostIdAndStatus(post.getId(), ContentStatus.PUBLISHED));
    }

    private Post findPost(Long postId) {
        return postRepository.findByIdAndStatus(postId, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void requireOwner(Post post, Long userId) {
        if (!post.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.NOT_POST_OWNER);
        }
    }
}
