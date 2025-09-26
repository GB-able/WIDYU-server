package com.widyu.domain.album.dto.response;

import com.widyu.domain.album.entity.AlbumUnlock;

import java.time.LocalDateTime;

public record AlbumUnlockResponse(
        Long unlockId,
        Long albumId,
        String albumTitle,
        LocalDateTime unlockedAt,
        String message
) {
    
    public static AlbumUnlockResponse from(AlbumUnlock albumUnlock) {
        return new AlbumUnlockResponse(
                albumUnlock.getId(),
                albumUnlock.getAlbum().getId(),
                albumUnlock.getAlbum().getContent().length() > 50 
                    ? albumUnlock.getAlbum().getContent().substring(0, 50) + "..." 
                    : albumUnlock.getAlbum().getContent(),
                albumUnlock.getUnlockedAt(),
                "앨범이 해금되었습니다."
        );
    }
}