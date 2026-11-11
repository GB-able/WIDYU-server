package com.widyu.device.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 기기 상태 바뀜 한 건(LLD-0129 3절). 본문 배열의 원소 하나다.
 * {@code kind}는 값 목록을 검증하지 않는다. 앱이 먼저 보낸 새 종류도 저장한다(길이만 본다).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceEventRequest(
        @JsonProperty("event_id")
        @NotNull(message = "이벤트 ID는 필수입니다.")
        @Pattern(regexp = "^[A-Za-z0-9._-]{1,64}$", message = "이벤트 ID는 영문·숫자·._- 1~64자여야 합니다.")
        String eventId,

        @JsonProperty("device_id")
        @NotNull(message = "기기 ID는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9._-]{1,64}$", message = "기기 ID는 소문자·숫자·._- 1~64자여야 합니다.")
        String deviceId,

        @NotNull(message = "출처는 필수입니다.")
        @Pattern(regexp = "^(watch|phone)$", message = "출처는 watch 또는 phone이어야 합니다.")
        String source,

        @NotBlank(message = "종류는 필수입니다.")
        @Size(max = 64, message = "종류는 64자 이하여야 합니다.")
        String kind,

        @JsonProperty("occurred_at_ms")
        @NotNull(message = "바뀐 시각은 필수입니다.")
        @Positive(message = "바뀐 시각은 0보다 커야 합니다.")
        Long occurredAtMs,

        @JsonProperty("session_id")
        @Pattern(regexp = "^[a-z0-9._-]{1,64}$", message = "세션 ID는 소문자·숫자·._- 1~64자여야 합니다.")
        String sessionId,

        /** 객체가 아니면(배열·문자열) 읽기 단계에서 실패해 그 항목만 거절된다. */
        ObjectNode attributes
) {
}
