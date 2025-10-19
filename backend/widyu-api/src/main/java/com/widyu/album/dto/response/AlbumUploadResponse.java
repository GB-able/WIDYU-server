package com.widyu.album.dto.response;

import com.widyu.album.Album;

import java.time.LocalDateTime;
import java.util.List;

public record AlbumUploadResponse(
        Long albumId,
        String content,
        List<String> mediaUrls,
        int mediaCount,
        int photoCount,
        int videoCount,
        String authorName,
        LocalDateTime createdAt
) {
    
    public static AlbumUploadResponse from(Album album) {
        return new AlbumUploadResponse(
                album.getId(),
                album.getContent(),
                album.getMediaUrls(),
                album.getMediaCount(),
                album.getPhotoCount(),
                album.getVideoCount(),
                album.getMember().getName(),
                album.getCreatedAt()
        );
    }
}