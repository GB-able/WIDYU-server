package com.widyu.domain.album.dto.response;

import com.widyu.domain.album.entity.Album;
import com.widyu.domain.album.entity.MediaType;

import java.time.LocalDateTime;
import java.util.List;

public record AlbumUploadResponse(
        Long albumId,
        String content,
        List<String> mediaUrls,
        int mediaCount,
        int photoCount,
        int videoCount,
        MediaType primaryMediaType,
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
                album.getPrimaryMediaType(),
                album.getMember().getName(),
                album.getCreatedAt()
        );
    }
}