package com.widyu.mypage.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public record ProfileImageUploadRequest(
        @NotNull(message = "이미지 파일은 필수입니다.")
        MultipartFile image
) {
}
