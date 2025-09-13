package com.widyu.domain.album.dto;

import java.util.List;

public record FamilyAlbumPageResponse(
        List<FamilyAlbumResponse> albums,
        boolean hasNext,
        Long nextCursor
) {
    public static FamilyAlbumPageResponse of(List<FamilyAlbumResponse> albums, boolean hasNext, Long nextCursor) {
        return new FamilyAlbumPageResponse(albums, hasNext, nextCursor);
    }

    public static FamilyAlbumPageResponse empty() {
        return new FamilyAlbumPageResponse(List.of(), false, null);
    }
}