package com.widyu.fcm.event.album.dto;

public record AlbumLikedEvent(Long albumId, Long likerMemberId, Long albumAuthorId) {
}