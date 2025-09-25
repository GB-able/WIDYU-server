package com.widyu.domain.album.dto.request;

public record AlbumFeedRequest(
        Long lastAlbumId
) {
    public boolean hasCursor() {
        return lastAlbumId != null;
    }

    public static AlbumFeedRequest from(Long lastAlbumId) {
        return new AlbumFeedRequest(lastAlbumId);
    }
}