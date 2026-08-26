package com.widyu.album.dto.response;

import com.widyu.album.AlbumUnlock;

import java.time.LocalDateTime;

public record AlbumUnlockResponse(
        Long unlockId,
        Long albumId,
        String albumTitle,
        LocalDateTime unlockedAt,
        Long remainingPoints,
        String message
) {
    
    public static AlbumUnlockResponse from(AlbumUnlock albumUnlock, Long remainingPoints) {
        return new AlbumUnlockResponse(
                albumUnlock.getId(),
                albumUnlock.getAlbum().getId(),
                albumUnlock.getAlbum().getContent().length() > 50 
                    ? albumUnlock.getAlbum().getContent().substring(0, 50) + "..." 
                    : albumUnlock.getAlbum().getContent(),
                albumUnlock.getUnlockedAt(),
                remainingPoints,
                "앨범이 해금되었습니다."
        );
    }
}