package com.widyu.heart.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HeartRateSendRequest(
        @NotNull(message = "심박수 데이터는 필수입니다.")
        @Size(min = 15, max = 15, message = "심박수 데이터는 정확히 15개여야 합니다.")
        @Valid
        List<HeartRateMeasurement> heartRates,

        String location, // 현재 위치 주소 (이상치 감지 시 위급상황 기록에 사용)

        @Pattern(
                regexp = "^(REST|LOW|ACTIVE|UNKNOWN|\\s*)$",
                message = "활동 상태는 REST, LOW, ACTIVE, UNKNOWN 중 하나여야 합니다."
        )
        String context
) {

    public static HeartRateSendRequest of(List<HeartRateMeasurement> heartRates, String location) {
        return new HeartRateSendRequest(heartRates, location, "UNKNOWN");
    }

    public static HeartRateSendRequest of(List<HeartRateMeasurement> heartRates, String location, String context) {
        return new HeartRateSendRequest(heartRates, location, context);
    }

    public String normalizedContext() {
        if (context == null || context.isBlank()) {
            return "UNKNOWN";
        }
        return context;
    }
}
