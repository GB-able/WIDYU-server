package com.widyu.album.dto.response;

import java.util.List;

public record UnlockedAlbumIdsResponse(
        List<Long> albumIds
) {
    public static UnlockedAlbumIdsResponse from(List<Long> albumIds) {
        return new UnlockedAlbumIdsResponse(albumIds);
    }
}