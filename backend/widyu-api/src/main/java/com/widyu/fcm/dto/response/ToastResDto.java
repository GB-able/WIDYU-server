package com.widyu.fcm.dto.response;

import lombok.Builder;

@Builder
public record ToastResDto(
        String title,
        String content,
        String scheme
) {
    public static ToastResDto from(String title) {
        return ToastResDto.builder()
                .title(title)
                .content("새로운 게시물을 올려주세요.")
                .scheme("")
                .build();
    }
}
