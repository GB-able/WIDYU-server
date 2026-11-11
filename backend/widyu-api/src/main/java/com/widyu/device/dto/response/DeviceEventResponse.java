package com.widyu.device.dto.response;

/** 항목 하나의 결과. {@code eventId}를 읽지 못한 REJECTED는 null이며, 응답 순서가 요청 순서와 같다. */
public record DeviceEventResponse(String eventId, DeviceEventResult result) {

    public static DeviceEventResponse of(String eventId, DeviceEventResult result) {
        return new DeviceEventResponse(eventId, result);
    }
}
