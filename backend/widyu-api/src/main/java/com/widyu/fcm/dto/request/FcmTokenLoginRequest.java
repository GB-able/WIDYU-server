package com.widyu.fcm.dto.request;

public record FcmTokenLoginRequest(
        String token,
        String deviceInfo
) {
}
