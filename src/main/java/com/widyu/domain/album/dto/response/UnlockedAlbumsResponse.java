package com.widyu.domain.album.dto.response;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.AlbumUnlock;
import com.widyu.domain.member.entity.Member;

import java.time.LocalDateTime;
import java.util.List;

public record UnlockedAlbumsResponse(
        List<UnlockedAlbumInfo> unlockedAlbums,
        Long totalCount,
        Long totalSpentPoints
) {

    public record UnlockedAlbumInfo(
            Long albumId,
            String content,
            List<String> mediaUrls,
            List<String> thumbnailUrls,
            LocalDateTime createdAt,
            AuthorInfo author,
            LocalDateTime unlockedAt
    ) {

        public record AuthorInfo(
                Long memberId,
                String name,
                String profileImage
        ) {
            public static AuthorInfo from(Member member) {
                return new AuthorInfo(
                        member.getId(),
                        member.getName(),
                        member.getProfileImage()
                );
            }
        }

        public static UnlockedAlbumInfo from(AlbumUnlock albumUnlock) {
            Album album = albumUnlock.getAlbum();
            return new UnlockedAlbumInfo(
                    album.getId(),
                    album.getContent(),
                    album.getMediaUrls(),
                    album.getThumbnailUrls(),
                    album.getCreatedAt(),
                    AuthorInfo.from(album.getMember()),
                    albumUnlock.getUnlockedAt()
            );
        }
    }

    public static UnlockedAlbumsResponse from(List<AlbumUnlock> unlockedAlbums, Long totalSpentPoints) {
        List<UnlockedAlbumInfo> albumInfos = unlockedAlbums.stream()
                .map(UnlockedAlbumInfo::from)
                .toList();

        return new UnlockedAlbumsResponse(
                albumInfos,
                (long) albumInfos.size(),
                totalSpentPoints
        );
    }
}