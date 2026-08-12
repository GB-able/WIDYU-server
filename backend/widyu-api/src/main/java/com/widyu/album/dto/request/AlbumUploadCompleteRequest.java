package com.widyu.album.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AlbumUploadCompleteRequest(
        @Size(max = 2200, message = "게시글 내용은 최대 2,200자까지 입력 가능합니다.")
        String content,

        @Valid
        List<@NotNull(message = "완료 파일 정보는 null일 수 없습니다.") CompletedFile> files
) {
    public record CompletedFile(
            @NotNull(message = "파일 index는 필수입니다.")
            Integer index,

            @NotEmpty(message = "파트 목록은 필수입니다.")
            @Valid
            List<@NotNull(message = "파트 정보는 null일 수 없습니다.") CompletedPart> parts
    ) {
    }

    public record CompletedPart(
            @NotNull(message = "파트 번호는 필수입니다.")
            Integer partNumber,

            @NotBlank(message = "파트 ETag는 필수입니다.")
            String eTag
    ) {
    }
}
