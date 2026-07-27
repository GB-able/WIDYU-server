package com.widyu.album.dto.response;

public record AlbumUploadAcceptedResponse(Long albumId) {

    public static AlbumUploadAcceptedResponse from(Long albumId) {
        return new AlbumUploadAcceptedResponse(albumId);
    }
}
