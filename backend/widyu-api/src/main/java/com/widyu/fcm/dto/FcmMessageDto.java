package com.widyu.fcm.dto;

import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@Builder
public record FcmMessageDto(
        boolean validateOnly,
        Message message
) {

    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Message(
            Notification notification,
            String token,
            Map<String, String> data,
            Android android,
            Apns apns
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Android(String ttl, String priority, AndroidNotification notification) {
        public Android(String ttl) {
            this(ttl, null, null);
        }
    }

    public record AndroidNotification(@JsonProperty("channel_id") String channelId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Apns(Map<String, String> headers, ApnsPayload payload) {
        public Apns(Map<String, String> headers) {
            this(headers, null);
        }
    }

    public record ApnsPayload(Aps aps) {}

    /** sound는 기본 소리 이름("default") 또는 {@link CriticalSound}다. 표시 알림이 아니면 null이다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Aps(
            @JsonProperty("interruption-level") String interruptionLevel,
            @JsonProperty("content-available") Integer contentAvailable,
            Object sound
    ) {}

    /** iOS Critical Alerts 소리. Apple entitlement와 사용자 허용이 없으면 기기가 critical 표시를 무시한다. */
    public record CriticalSound(int critical, String name, double volume) {}

    @Builder
    public record Notification(
            String title,
            String body,
            String image
    ) {}
}
