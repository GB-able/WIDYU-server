package com.widyu.sensor.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 워치 IMU 큐가 넘쳐 버린 구간(LLD-0124 3절). 센서 배치와 같은 endpoint로 오고 {@code stream: "imu_dropped"}로 갈린다.
 * 서버는 원문을 그대로 보관하므로 모르는 필드를 거절하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SensorDroppedRangeRequest(
        @JsonProperty("session_id")
        @NotNull(message = "세션 ID는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9._-]{1,64}$", message = "세션 ID는 소문자·숫자·._- 1~64자여야 합니다.")
        String sessionId,

        @JsonProperty("from_seq")
        @NotNull(message = "시작 순번은 필수입니다.")
        @PositiveOrZero(message = "시작 순번은 0 이상이어야 합니다.")
        Long fromSeq,

        @JsonProperty("to_seq")
        @NotNull(message = "끝 순번은 필수입니다.")
        @PositiveOrZero(message = "끝 순번은 0 이상이어야 합니다.")
        Long toSeq,

        @NotNull(message = "결측 사유는 필수입니다.")
        @Size(min = 1, max = 64, message = "결측 사유는 1~64자여야 합니다.")
        String reason,

        @JsonProperty("ts_ms")
        @NotNull(message = "기록 시각은 필수입니다.")
        Long tsMs
) {
}
