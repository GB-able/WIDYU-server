package com.widyu.album.dto.response;

import com.widyu.album.Album;
import com.widyu.member.Member;
import com.widyu.album.MediaType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public record AlbumFeedResponse(
        Long albumId,
        String authorName,
        String authorProfileImage,
        String content,
        List<String> mediaUrls,
        List<String> thumbnailUrls,
        String thumbnailUrl,
        MediaType primaryMediaType,
        Integer mediaCount,
        Integer photoCount,
        Integer videoCount,
        Integer likeCount,
        Integer commentCount,
        Integer viewCount,
        List<ViewerInfo> viewers,
        LocalDateTime createdAt,
        Boolean canEdit,
        Boolean isUnlocked,
        Long price,
        String videoDuration // 동영상인 경우만
) {
    public record ViewerInfo(
            String name,
            String profileImage
    ) {
        public static ViewerInfo from(Member member) {
            return new ViewerInfo(member.getName(), member.getProfileImage());
        }
    }

    public static AlbumFeedResponse from(Album album, Boolean canEdit, Boolean isUnlocked, List<ViewerInfo> viewers) {
        String primaryVideoDuration = album.getDurations().stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(d -> String.format("%d:%02d", d / 60, d % 60))
                .orElse(null);

        // 잠금 전에는 원본 URL과 썸네일 목록을 싣지 않고 대표 썸네일 thumbnailUrl만 준다(#813). 사진은 썸네일이 곧 원본이다
        List<String> mediaUrls = List.of();
        List<String> thumbnailUrls = List.of();
        if (Boolean.TRUE.equals(isUnlocked)) {
            mediaUrls = album.getMediaUrls();
            thumbnailUrls = album.getThumbnailUrls();
        }

        return new AlbumFeedResponse(
                album.getId(),
                album.getMember().getName(),
                album.getMember().getProfileImage(),
                album.getContent(),
                mediaUrls,
                thumbnailUrls,
                album.getThumbnailUrls().stream().findFirst().orElse(null),
                album.getPrimaryMediaType(),
                album.getMediaCount(),
                album.getPhotoCount(),
                album.getVideoCount(),
                album.getLikeCount(),
                album.getCommentCount(),
                album.getViewCount(),
                viewers,
                album.getCreatedAt(),
                canEdit,
                isUnlocked,
                Album.UNLOCK_PRICE,
                primaryVideoDuration
        );
    }
}