package com.widyu.album.dto.response;

import java.util.List;

public record LikedAlbumsResponse(
        List<Long> albumIds
) {
    
    public static LikedAlbumsResponse from(List<Long> albumIds) {
        return new LikedAlbumsResponse(albumIds);
    }
}