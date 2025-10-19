package com.widyu.fcm.api.dto.response;

import com.widyu.fcm.api.dto.FcmSendDto;

public record FcmSendResponse(
        String title,
        String body,
        String scheme,
        int successCount
) {
    public static FcmSendResponse of(FcmSendDto fcmSendDto, int successCount) {
        return new FcmSendResponse(fcmSendDto.title(), fcmSendDto.content(), fcmSendDto.scheme(), successCount);
    }
}

