package com.widyu.album;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AlbumUploadSessionStatus {
    WAITING("업로드 대기"),
    COMPLETED("완료");

    private final String description;
}
