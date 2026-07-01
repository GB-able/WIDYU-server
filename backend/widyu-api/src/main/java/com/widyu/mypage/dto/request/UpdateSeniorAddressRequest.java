package com.widyu.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateSeniorAddressRequest(
        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 최대 200자입니다.")
        String address,

        @Size(max = 200, message = "상세주소는 최대 200자입니다.")
        String detailAddress,

        @NotNull(message = "위도는 필수입니다.")
        String latitude,

        @NotNull(message = "경도는 필수입니다.")
        String longitude
) {}
