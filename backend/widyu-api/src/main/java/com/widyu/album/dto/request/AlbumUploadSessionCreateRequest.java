package com.widyu.album.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AlbumUploadSessionCreateRequest(
        @NotNull(message = "업로드할 파일 정보는 필수입니다.")
        @Size(min = 1, max = 8, message = "파일은 최소 1개, 최대 8개까지 업로드 가능합니다.")
        @Valid
        List<@NotNull(message = "파일 정보는 null일 수 없습니다.") FileMetadata> files
) {
    public record FileMetadata(
            @NotBlank(message = "파일 이름은 필수입니다.")
            String fileName,

            @NotBlank(message = "파일 타입은 필수입니다.")
            String contentType,

            @NotNull(message = "파일 크기는 필수입니다.")
            @Positive(message = "파일 크기는 양수여야 합니다.")
            Long fileSize
    ) {
    }
}
