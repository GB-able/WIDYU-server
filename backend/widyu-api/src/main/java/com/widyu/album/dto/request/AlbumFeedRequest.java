package com.widyu.album.dto.request;

import java.time.LocalDateTime;

public record AlbumFeedRequest(
        LocalDateTime lastCreatedAt,
        Long lastAlbumId,
        String date
) {
    public boolean hasCursor() {
        return lastCreatedAt != null && lastAlbumId != null;
    }

    public boolean hasDate() {
        return date != null && !date.trim().isEmpty();
    }

    public static AlbumFeedRequest from(String cursor, String date) {
        if (cursor == null) {
            return new AlbumFeedRequest(null, null, date);
        }
        String[] parts = cursor.split("\\|");
        LocalDateTime lastCreatedAt = LocalDateTime.parse(parts[0]);
        Long lastAlbumId = Long.parseLong(parts[1]);
        return new AlbumFeedRequest(lastCreatedAt, lastAlbumId, date);
    }
}
