package com.widyu.heart.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "워치앱 심박수 전송 요청")
public record HeartRateSendRequest(
        @Schema(description = "심박수 데이터 목록 (정확히 15개)")
        @NotNull(message = "심박수 데이터는 필수입니다.")
        @Size(min = 15, max = 15, message = "심박수 데이터는 정확히 15개여야 합니다.")
        @Valid
        List<HeartRateMeasurement> heartRates
) {
}
