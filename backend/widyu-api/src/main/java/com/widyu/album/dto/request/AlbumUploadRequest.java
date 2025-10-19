package com.widyu.album.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public record AlbumUploadRequest(
        @Size(max = 2200, message = "게시글 내용은 최대 2,200자까지 입력 가능합니다.")
        String content,
        
        @NotNull(message = "미디어 파일은 필수입니다.")
        @Size(min = 1, max = 8, message = "미디어 파일은 최소 1개, 최대 8개까지 업로드 가능합니다.")
        List<MultipartFile> mediaFiles
) {
    // 순수한 데이터 전달 객체 - 비즈니스 로직 없음
}