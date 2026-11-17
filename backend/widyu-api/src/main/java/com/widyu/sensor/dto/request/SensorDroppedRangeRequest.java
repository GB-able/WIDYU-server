package com.widyu.sensor.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 기기가 버린 묶음 구간(LLD-0124 3절, LLD-0166 3.1). 센서 배치와 같은 endpoint로 오고
 * {@code stream: "dropped"}(새 형식) 또는 {@code "imu_dropped"}(워치 IMU 전용 옛 형식)로 갈린다.
 * 서버는 원문을 그대로 보관하므로 모르는 필드를 거절하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SensorDroppedRangeRequest(
        @JsonProperty("session_id")
        @NotNull(message = "세션 ID는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9._-]{1,64}$", message = "세션 ID는 소문자·숫자·._- 1~64자여야 합니다.")
        String sessionId,

        /** 버린 묶음의 stream. 있으면 형식을 보고, 새 형식에서만 필수다(서비스가 확인). 옛 형식은 저장에 쓰지 않는다. */
        @JsonProperty("dropped_stream")
        @Pattern(regexp = "^[a-z0-9_]{1,20}$", message = "데이터 종류는 소문자·숫자·_ 1~20자여야 합니다.")
        String droppedStream,

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
