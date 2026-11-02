package com.widyu.mypage.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSeniorAddressRequest(
        @Schema(example = "서울특별시 마포구 성암로 301")
        @NotBlank(message = "주소는 필수입니다.")
        @Size(max = 200, message = "주소는 최대 200자입니다.")
        String address,

        @Schema(description = "주소의 위도. 경도와 함께 보내면 주소 좌표 변환 대신 이 값을 씁니다. 보내지 않으면 서버가 주소로 좌표를 구합니다.", example = "37.5796")
        @DecimalMin(value = "-90", message = "위도는 -90 이상 90 이하여야 합니다.")
        @DecimalMax(value = "90", message = "위도는 -90 이상 90 이하여야 합니다.")
        Double latitude,

        @Schema(description = "주소의 경도. 위도와 함께 보내야 합니다.", example = "126.8924")
        @DecimalMin(value = "-180", message = "경도는 -180 이상 180 이하여야 합니다.")
        @DecimalMax(value = "180", message = "경도는 -180 이상 180 이하여야 합니다.")
        Double longitude
) {

    @Schema(hidden = true)
    @AssertTrue(message = "위도와 경도는 함께 보내거나 둘 다 생략해야 합니다.")
    public boolean isCoordinatePaired() {
        return (latitude == null) == (longitude == null);
    }
}
