package com.widyu.domain.album.dto.request;

public record AlbumFeedRequest(
        Long lastAlbumId,
        String date
) {
    public boolean hasCursor() {
        return lastAlbumId != null;
    }

    public static AlbumFeedRequest from(Long lastAlbumId, String date) {
        return new AlbumFeedRequest(lastAlbumId, date);
    }
    
    public boolean hasDate() {
        return date != null && !date.trim().isEmpty();
    }
}