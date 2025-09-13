package com.widyu.domain.album.dto;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.member.entity.Member;

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
        AuthorInfo author
) {
    public static FamilyAlbumResponse from(Album album) {
        Member member = album.getMember();

        List<String> urls = album.getMediaUrls() != null
                ? List.copyOf(album.getMediaUrls())
                : List.of();

        return new FamilyAlbumResponse(
                album.getId(),
                album.getContent(),
                urls,
                album.getLikeCount(),
                album.getCommentCount(),
                album.getViewCount(),
                album.getCreatedAt(),
                AuthorInfo.from(member)
        );
    }

    public record AuthorInfo(
            Long memberId,
            String name
    ) {
        public static AuthorInfo from(Member member) {
            return new AuthorInfo(
                    member.getId(),
                    member.getName()
            );
        }
    }
}
