package com.widyu.album.dto;

import com.widyu.album.Album;
import com.widyu.member.Member;

import java.time.LocalDateTime;
import java.util.List;

public record FamilyAlbumResponse(
        Long albumId,
        String content,
        List<String> mediaUrls,
        Integer likeCount,
        Integer commentCount,
        Integer viewCount,
        LocalDateTime createdAt,
        AuthorInfo author,
        List<ViewerInfo> viewers
) {
    public static FamilyAlbumResponse from(Album album, List<Member> viewers) {
        Member member = album.getMember();

        List<String> urls = album.getMediaUrls() != null
                ? List.copyOf(album.getMediaUrls())
                : List.of();

        List<ViewerInfo> viewerInfos = viewers.stream()
                .map(ViewerInfo::from)
                .toList();

        return new FamilyAlbumResponse(
                album.getId(),
                album.getContent(),
                urls,
                album.getLikeCount(),
                album.getCommentCount(),
                album.getViewCount(),
                album.getCreatedAt(),
                AuthorInfo.from(member),
                viewerInfos
        );
    }

    public record AuthorInfo(
            Long memberId,
            String name,
            String profileImage
    ) {
        public static AuthorInfo from(Member member) {
            return new AuthorInfo(
                    member.getId(),
                    member.getName(),
                    null  // TODO: 프로필 이미지 필드가 추가되면 수정
            );
        }
    }

    public record ViewerInfo(
            Long memberId,
            String name,
            String profileImage
    ) {
        public static ViewerInfo from(Member member) {
            return new ViewerInfo(
                    member.getId(),
                    member.getName(),
                    null  // TODO: 프로필 이미지 필드가 추가되면 수정
            );
        }
    }
}
