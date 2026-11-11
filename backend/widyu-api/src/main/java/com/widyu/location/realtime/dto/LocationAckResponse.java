package com.widyu.location.realtime.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 발신 세션에만 보내는 위치 ACK(LLD-0123). 기존 ACK 필드는 {@code location}을 풀어 그대로 싣고,
 * 앱이 큐 항목을 찾을 요청 식별자와 처리 결과를 덧붙인다. 보호자 방송과 REST 조회는 {@link LocationUpdateResponse}를 그대로 쓴다.
 *
 * <p>구형 위치(원본 저장 대상이 아님)는 식별자와 결과가 null이다. 거절이면 {@code location}이 null이다.
 */
public record LocationAckResponse(
        @JsonUnwrapped LocationUpdateResponse location,
        String deviceId,
        String sessionId,
        Long seq,
        LocationAckResult result
) {

    public static LocationAckResponse of(LocationUpdateResponse location, LocationUpdateRequest request,
                                         LocationAckResult result) {
        return new LocationAckResponse(location, request.deviceId(), request.sessionId(), request.seq(), result);
    }

    public static LocationAckResponse rejected(LocationUpdateRequest request) {
        return new LocationAckResponse(null, request.deviceId(), request.sessionId(), request.seq(),
                LocationAckResult.REJECTED);
    }

    /** 원본을 저장하지 않는 구형 위치의 ACK. 실시간 처리 완료만 뜻한다. */
    public static LocationAckResponse legacy(LocationUpdateResponse location) {
        return new LocationAckResponse(location, null, null, null, null);
    }
}
