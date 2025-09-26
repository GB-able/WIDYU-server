package com.widyu.domain.album.dto.response;

import com.widyu.domain.album.entity.AlbumComment;

import java.time.LocalDateTime;
import java.util.List;

public record AlbumCommentResponse(
        Long commentId,
        Long albumId,
        String content,
        String authorName,
        Long authorId,
        Integer likeCount,
        boolean isReply,
        Long parentCommentId,
        List<AlbumCommentResponse> replies,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    
    public static AlbumCommentResponse from(AlbumComment comment) {
        return new AlbumCommentResponse(
                comment.getId(),
                comment.getAlbum().getId(),
                comment.getContent(),
                comment.getMember().getName(),
                comment.getMember().getId(),
                comment.getLikeCount(),
                comment.getParentComment() != null,
                comment.getParentComment() != null ? comment.getParentComment().getId() : null,
                comment.getReplies().stream()
                        .map(AlbumCommentResponse::from)
                        .toList(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
    
    public static AlbumCommentResponse fromWithoutReplies(AlbumComment comment) {
        return new AlbumCommentResponse(
                comment.getId(),
                comment.getAlbum().getId(),
                comment.getContent(),
                comment.getMember().getName(),
                comment.getMember().getId(),
                comment.getLikeCount(),
                comment.getParentComment() != null,
                comment.getParentComment() != null ? comment.getParentComment().getId() : null,
                List.of(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }
}