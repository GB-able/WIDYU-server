package com.widyu.domain.fcm.event.album.dto;

public record AlbumCommentedEvent(Long albumId, Long commenterMemberId, Long albumAuthorId) {
}