package com.widyu.fcm.dto.response;

import com.widyu.fcm.FcmNotification;
import java.util.List;

public record FcmNotificationResponses(
        List<FcmNotificationResponse> notifications,
        boolean hasNext,
        Long nextCursor
) {
    public static FcmNotificationResponses from(List<FcmNotification> list) {
        return new FcmNotificationResponses(
                list.stream().map(FcmNotificationResponse::from).toList(),
                false,
                null
        );
    }

    public static FcmNotificationResponses of(List<FcmNotification> list, boolean hasNext, Long nextCursor) {
        return new FcmNotificationResponses(
                list.stream().map(FcmNotificationResponse::from).toList(),
                hasNext,
                nextCursor
        );
    }

    public static FcmNotificationResponses empty() {
        return new FcmNotificationResponses(List.of(), false, null);
    }
}
