package com.widyu.domain.album.dto.request;

import jakarta.validation.constraints.Size;

public record AlbumUpdateRequest(
        @Size(max = 2200, message = "게시글 내용은 최대 2,200자까지 입력 가능합니다.")
        String content
) {
}