package com.zipsa.community;

import com.zipsa.common.ApiResponse;
import com.zipsa.community.dto.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 08 커뮤니티 화면. 조회는 비로그인 허용, 쓰기는 로그인 필요. */
@RestController
@RequestMapping("/api/posts")
public class CommunityController {

    private final CommunityService service;

    public CommunityController(CommunityService service) {
        this.service = service;
    }

    /** 오퍼레이션 21 — 게시글 목록 */
    @GetMapping
    public ApiResponse<Page<PostSummaryResponse>> getPosts(
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.getPosts(category, keyword, PageRequest.of(page, size)));
    }

    /** 01 메인 인기글 */
    @GetMapping("/popular")
    public ApiResponse<List<PostSummaryResponse>> getPopular(
            @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.ok(service.getPopular(PageRequest.of(0, size)));
    }

    /** 오퍼레이션 22 — 상세. 비로그인도 볼 수 있으므로 userId 가 null 일 수 있다. */
    @GetMapping("/{postId}")
    public ApiResponse<PostDetailResponse> getPost(@PathVariable Long postId,
                                                   @AuthenticationPrincipal Long userId) {
        return ApiResponse.ok(service.getPost(postId, userId));
    }

    /** 오퍼레이션 23 — 작성 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> write(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody PostRequest request) {
        return ApiResponse.ok(Map.of("postId", service.writePost(userId, request)));
    }

    /** 오퍼레이션 24 — 수정 */
    @PatchMapping("/{postId}")
    public ApiResponse<Void> edit(@AuthenticationPrincipal Long userId, @PathVariable Long postId,
                                  @Valid @RequestBody PostRequest request) {
        service.editPost(userId, postId, request);
        return ApiResponse.ok();
    }

    /** 오퍼레이션 25 — 삭제 */
    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long postId) {
        service.deletePost(userId, postId);
        return ApiResponse.ok();
    }

    /** 오퍼레이션 26 — 좋아요 토글 */
    @PostMapping("/{postId}/likes")
    public ApiResponse<Map<String, Boolean>> like(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long postId) {
        return ApiResponse.ok(Map.of("liked", service.toggleLike(userId, postId)));
    }

    /** 오퍼레이션 27 — 댓글 작성 */
    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Map<String, Long>> comment(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long postId,
                                                  @Valid @RequestBody CommentRequest request) {
        return ApiResponse.ok(Map.of("commentId", service.writeComment(userId, postId, request)));
    }

    /** 오퍼레이션 28 — 댓글 삭제 */
    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long commentId) {
        service.deleteComment(userId, commentId);
        return ApiResponse.ok();
    }
}
