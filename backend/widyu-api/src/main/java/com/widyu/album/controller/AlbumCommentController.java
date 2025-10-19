package com.widyu.album.controller;

import com.widyu.album.controller.docs.AlbumCommentDocs;
import com.widyu.album.application.AlbumCommentService;
import com.widyu.album.dto.request.AlbumCommentCreateRequest;
import com.widyu.album.dto.request.AlbumCommentUpdateRequest;
import com.widyu.album.dto.response.AlbumCommentResponse;
import com.widyu.global.response.ApiResponseTemplate;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/albums")
public class AlbumCommentController implements AlbumCommentDocs {

    private final AlbumCommentService albumCommentService;

    @Override
    @PostMapping("/{albumId}/comments")
    public ApiResponseTemplate<AlbumCommentResponse> createComment(
            @PathVariable Long albumId,
            @Valid @RequestBody AlbumCommentCreateRequest request
    ) {
        AlbumCommentResponse response = albumCommentService.createComment(albumId, request);
        return ApiResponseTemplate.ok()
                .code("ALBM_COMMENT_2001")
                .message("댓글이 생성되었습니다.")
                .body(response);
    }

    @Override
    @PutMapping("/comments/{commentId}")
    public ApiResponseTemplate<AlbumCommentResponse> updateComment(
            @PathVariable Long commentId,
            @Valid @RequestBody AlbumCommentUpdateRequest request
    ) {
        AlbumCommentResponse response = albumCommentService.updateComment(commentId, request);
        return ApiResponseTemplate.ok()
                .code("ALBM_COMMENT_2003")
                .message("댓글이 수정되었습니다.")
                .body(response);
    }

    @Override
    @DeleteMapping("/comments/{commentId}")
    public ApiResponseTemplate<Void> deleteComment(@PathVariable Long commentId) {
        albumCommentService.deleteComment(commentId);
        return ApiResponseTemplate.ok()
                .code("ALBM_COMMENT_2004")
                .message("댓글이 삭제되었습니다.")
                .build();
    }
}
