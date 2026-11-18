package com.widyu.fcm.dto;

import com.widyu.fcm.FcmCategory;
import com.widyu.fcm.FcmOutbox;
import com.widyu.fcm.NotificationType;
import com.widyu.global.error.BusinessException;
import com.widyu.global.error.ErrorCode;
import com.widyu.member.Member;
import lombok.Builder;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Builder
public record FcmSendDto(
        String title,
        String content,
        FcmCategory fcmCategory,
        String scheme,
        String image,
        boolean emergency,
        Long relatedMemberId,
        Map<String, String> data,
        /** 이 알림을 낳은 판정 기록. 전송이 성공하면 그 행에 도달 사실을 채운다(LLD-0053 5.2). 대개 null이다. */
        String decisionId,
        NotificationType notificationType,
        String eventId,
        String deepLink,
        String entityId,
        Long seniorId,
        String actorDisplayName,
        String effectiveFromDate,
        String groupKey,
        String centerTitle,
        String centerBody,
        String seniorDisplayName,
        Integer remainingLockedCount,
        /**
         * 푸시를 버릴 시각의 상한. 이 시각이 지나면 보내지 않고 FCM 보관도 이 시각까지다. null이면 일반·위급 TTL만 쓴다.
         * 복약 인증 푸시처럼 시간이 지나면 내용이 틀려지는 알림에 준다(LLD-0127).
         */
        LocalDateTime pushExpiresAt
) {
    public FcmSendDto {
        if (data == null) {
            data = Map.of();
        }
    }

    public FcmSendDto(String title, String content, FcmCategory category, String scheme, String image) {
        this(title, content, category, scheme, image, false, null, Map.of(), null);
    }

    public FcmSendDto(String title, String content, FcmCategory category, String scheme, String image,
            boolean emergency, Long relatedMemberId, Map<String, String> data, String decisionId) {
        this(title, content, category, scheme, image, emergency, relatedMemberId, data, decisionId,
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public static FcmSendDto from(FcmOutbox row, Map<String, String> restoredData) {
        return FcmSendDto.builder().title(row.getTitle()).content(row.getBody())
                .fcmCategory(row.getFcmCategory()).scheme(row.getScheme()).image(row.getImage())
                .emergency(row.isEmergency()).relatedMemberId(row.getRelatedMemberId())
                .data(restoredData).decisionId(row.getDecisionId())
                .notificationType(row.getNotificationType()).build();
    }

    public static FcmSendDto of(NotificationType type, NotificationCopy copy, String entityId,
            String deepLink, Long relatedMemberId, Long seniorId, String image) {
        return FcmSendDto.builder().title(copy.title()).content(copy.body())
                .fcmCategory(type.fcmCategory()).notificationType(type)
                .entityId(entityId).deepLink(deepLink).relatedMemberId(relatedMemberId)
                .seniorId(seniorId).image(image).build();
    }

    /** 응원(X02), 하트(X01) 메시지 최대 글자 수(그래핌). 앱 입력창 50자와 같은 기준이다(LLD-0167 6절). */
    public static final int MESSAGE_MAX_LENGTH = 50;
    /** 알림센터 본문 칸(fcm_notification.body VARCHAR(255)) 상한. 결합 문자가 많은 한 글자는 NFC 뒤 255를 넘을 수 있다. */
    private static final int CENTER_BODY_MAX_CODE_POINTS = 255;

    /**
     * 가족 메시지 알림. 잠금화면(title, content)에는 고정 문구만 두고 미리보기는 앱 안 문구(centerTitle,
     * centerBody, data.inAppTitle/inAppBody)와 알림센터에만 싣는다. 원문은 어디에도 남기지 않는다(LLD-0167).
     */
    public static FcmSendDto ofMessage(NotificationType type, Member sender, String message) {
        String text = NotificationCopy.normalizeMessage(message);
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메시지 내용을 입력해 주세요.");
        }
        if (NotificationCopy.graphemeCount(text) > MESSAGE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메시지는 50자 이하로 입력해 주세요.");
        }
        Map<String, String> values = Map.of(
                "보낸 사람 이름", Objects.requireNonNullElse(sender.getName(), ""),
                "메시지 미리보기", text);
        String code = type.copyCode().substring(0, 3);
        NotificationCopy push = NotificationCopy.of(type, code + "-OS", values);
        NotificationCopy inApp = NotificationCopy.of(type, code + "-INAPP", values);
        if (inApp.body().codePointCount(0, inApp.body().length()) > CENTER_BODY_MAX_CODE_POINTS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "메시지에 쓸 수 없는 글자가 너무 많아요.");
        }
        return FcmSendDto.builder().title(push.title()).content(push.body())
                .fcmCategory(type.fcmCategory()).notificationType(type)
                .image(sender.getProfileImage()).relatedMemberId(sender.getId())
                .actorDisplayName(sender.getName())
                .centerTitle(inApp.title()).centerBody(inApp.body()).build();
    }

    /** type이 없는 기존 호출자의 data는 그대로 둔다. */
    public Map<String, String> dataForEnqueue(String resolvedEventId) {
        if (notificationType == null) {
            return data;
        }
        HashMap<String, String> values = new HashMap<>(data);
        values.put("eventId", resolvedEventId);
        values.put("notificationType", notificationType.name());
        values.put("deepLink", resolvedDeepLink());
        putIfPresent(values, "entityId", entityId);
        if (seniorId != null) {
            values.put("seniorId", seniorId.toString());
        }
        putIfPresent(values, "actorDisplayName", actorDisplayName);
        putIfPresent(values, "effectiveFromDate", effectiveFromDate);
        putIfPresent(values, "groupKey", groupKey);
        putIfPresent(values, "inAppTitle", centerTitle);
        putIfPresent(values, "inAppBody", centerBody);
        putIfPresent(values, "seniorDisplayName", seniorDisplayName);
        if (remainingLockedCount != null) {
            values.put("remainingLockedCount", remainingLockedCount.toString());
        }
        return Map.copyOf(values);
    }

    public String centerTitleOrTitle() {
        if (centerTitle != null) {
            return centerTitle;
        }
        return title;
    }

    public String centerBodyOrContent() {
        if (centerBody != null) {
            return centerBody;
        }
        return content;
    }

    private String resolvedDeepLink() {
        if (deepLink != null && !deepLink.isBlank()) {
            return deepLink;
        }
        if (scheme != null && !scheme.isBlank()) {
            return scheme;
        }
        String template = notificationType.deepLinkTemplate();
        if (template == null) {
            return "";
        }
        if (entityId != null) {
            template = template.replace("{entityId}", entityId);
        }
        if (seniorId != null) {
            template = template.replace("{seniorId}", seniorId.toString());
        }
        String commentId = data.get("commentId");
        if (commentId != null) {
            template = template.replace("{commentId}", commentId);
        }
        if (template.contains("{")) {
            return "";
        }
        return template;
    }

    private static void putIfPresent(Map<String, String> values, String key, String value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    public FcmSendDto withRelatedMember(Long memberId) {
        return new FcmSendDto(title, content, fcmCategory, scheme, image, emergency, memberId, data, decisionId,
                notificationType, eventId, deepLink, entityId, seniorId, actorDisplayName, effectiveFromDate, groupKey,
                centerTitle, centerBody, seniorDisplayName, remainingLockedCount, pushExpiresAt);
    }

    public FcmSendDto withData(Map<String, String> newData) {
        return new FcmSendDto(title, content, fcmCategory, scheme, image, emergency, relatedMemberId, newData, decisionId,
                notificationType, eventId, deepLink, entityId, seniorId, actorDisplayName, effectiveFromDate, groupKey,
                centerTitle, centerBody, seniorDisplayName, remainingLockedCount, pushExpiresAt);
    }

    public FcmSendDto withCenterCopy(NotificationCopy copy) {
        return new FcmSendDto(title, content, fcmCategory, scheme, image, emergency, relatedMemberId, data, decisionId,
                notificationType, eventId, deepLink, entityId, seniorId, actorDisplayName, effectiveFromDate, groupKey,
                copy.title(), copy.body(), seniorDisplayName, remainingLockedCount, pushExpiresAt);
    }

    public FcmSendDto withSeniorUnlockDetails(String displayName, Integer count) {
        return new FcmSendDto(title, content, fcmCategory, scheme, image, emergency, relatedMemberId, data, decisionId,
                notificationType, eventId, deepLink, entityId, seniorId, actorDisplayName, effectiveFromDate, groupKey,
                centerTitle, centerBody, displayName, count, pushExpiresAt);
    }
}
