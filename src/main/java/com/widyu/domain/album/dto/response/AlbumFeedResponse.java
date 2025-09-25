package com.widyu.domain.album.dto.response;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.MediaType;
import java.time.LocalDateTime;
import java.util.List;

public record AlbumFeedResponse(
        Long albumId,
        String authorName,
        String authorProfileImage,
        String content,
        List<String> mediaUrls,
        List<String> thumbnailUrls,
        MediaType primaryMediaType,
        Integer mediaCount,
        Integer photoCount,
        Integer videoCount,
        Integer likeCount,
        Integer commentCount,
        Integer viewCount,
        List<ViewerInfo> viewers,
        LocalDateTime createdAt,
        Boolean isLikedByCurrentUser,
        String videoDuration // 동영상인 경우만
) {
    public record ViewerInfo(
            String name,
            String profileImage
    ) {}

    public static AlbumFeedResponse from(Album album, Boolean isLikedByCurrentUser, List<ViewerInfo> viewers) {
        return new AlbumFeedResponse(
                album.getId(),
                album.getMember().getName(),
                null, // TODO: 프로필 이미지 구현 시 추가
                album.getContent(),
                album.getMediaUrls(),
                album.getThumbnailUrls(),
                album.getPrimaryMediaType(),
                album.getMediaCount(),
                album.getPhotoCount(),
                album.getVideoCount(),
                album.getLikeCount(),
                album.getCommentCount(),
                album.getViewCount(),
                viewers,
                album.getCreatedAt(),
                isLikedByCurrentUser,
                null // TODO: 비디오 지속시간 구현 시 추가
        );
    }
}