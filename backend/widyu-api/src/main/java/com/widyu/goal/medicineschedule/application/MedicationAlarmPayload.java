package com.widyu.goal.medicineschedule.application;

import com.widyu.fcm.NotificationType;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

/**
 * 약 관련 FCM에 싣는 복약 알람 동기화 data. 앱은 받으면 alarm-sync API로 스냅샷을 다시 조회한다.
 * revision은 받는 회원의 값이다(API가 호출자 본인의 스냅샷을 돌려준다). → LLD-0037
 */
public final class MedicationAlarmPayload {

    private MedicationAlarmPayload() {
    }

    public static Map<String, String> of(long revision, NotificationType notificationType) {
        return Map.of("notificationType", notificationType.name(), "revision", Long.toString(revision));
    }

    public static String eventId(String key) {
        return UUID.nameUUIDFromBytes(("medication:" + key).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
