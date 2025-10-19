package com.widyu.fcm.dto.response;

import com.widyu.fcm.dto.FcmSendDto;

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

