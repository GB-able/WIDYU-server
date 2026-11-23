package com.widyu.retention.application;

/**
 * 탈퇴 30일 뒤 지우는 회원 자료와 그 순서(LLD-0187 4.1). FK를 가리키는 쪽부터 지운다.
 *
 * <p>조건은 이 상수에서만 SQL에 들어간다. 건수와 삭제가 같은 조건을 쓴다. {@code :memberId}가 탈퇴 회원이다.
 * 결제, 동의, 관리자 기록, 탈퇴 요청, 실증 참여와 회차, 소셜 연동 해제와 S3 삭제 작업 표는 대상이 아니다(결정 7).
 */
public enum WithdrawnMemberPurgeStep {

    // 탈퇴자가 올린 앨범과 딸린 자료(2026-11-23 사용자 결정: 모두 삭제). 답글이 원댓글을 가리키므로 답글부터.
    ALBUM_COMMENT_REPLY("album_comment",
            "parent_comment_id IS NOT NULL AND album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)",
            false),
    // 답글은 앞 단계 몫이다. 원댓글로 한정해야 dry-run 건수가 실제 삭제 건수와 같다.
    ALBUM_COMMENT("album_comment",
            "parent_comment_id IS NULL AND album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)",
            false),
    ALBUM_LIKE("album_like", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)", false),
    ALBUM_VIEW("album_view", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)", false),
    ALBUM_UNLOCK("album_unlock", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)", false),
    ALBUM_DURATION("album_duration", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)", false),
    ALBUM_MEDIA_URL("album_media_url", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)", false),
    ALBUM_THUMBNAIL_URL("album_thumbnail_url",
            "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)", false),
    ALBUM("album", "member_id = :memberId", false),

    MEDICATION_PROOF_IMAGES("medication_proof_images",
            "medication_proof_id IN (SELECT id FROM medication_proof WHERE member_id = :memberId)", false),
    MEDICATION_PROOF("medication_proof", "member_id = :memberId", false),
    MEDICINE_SCHEDULE_DETAIL("medicine_schedule_detail",
            "medicine_category_id IN (SELECT c.id FROM medicine_category c JOIN medicine_schedule s"
                    + " ON c.medicine_schedule_id = s.id WHERE s.member_id = :memberId)", false),
    MEDICINE_CATEGORY("medicine_category",
            "medicine_schedule_id IN (SELECT id FROM medicine_schedule WHERE member_id = :memberId)", false),
    MEDICINE_SCHEDULE("medicine_schedule", "member_id = :memberId", false),
    HEALTH_SCHEDULE("health_schedule", "member_id = :memberId", false),
    WALK("walk", "member_id = :memberId", false),
    ADDRESS_BOOKMARK("address_bookmark", "member_id = :memberId", false),
    PARENT_LOCATION("parent_location", "member_id = :memberId", false),
    SENIOR_STAY_STATE("senior_stay_state", "member_id = :memberId", false),
    MEMBER_NOTIFICATION_SETTING("member_notification_setting", "member_id = :memberId", false),

    // 발송 대기가 알림센터 행과 기기 토큰을 가리키므로 먼저 지운다.
    FCM_OUTBOX("fcm_outbox",
            "recipient_member_id = :memberId"
                    + " OR member_fcm_token_id IN (SELECT id FROM member_fcm_token WHERE member_id = :memberId)"
                    + " OR notification_id IN (SELECT id FROM fcm_notification WHERE recipient_member_id = :memberId)",
            false),
    FCM_NOTIFICATION("fcm_notification",
            "recipient_member_id = :memberId"
                    + " OR member_fcm_token_id IN (SELECT id FROM member_fcm_token WHERE member_id = :memberId)",
            false),
    MEMBER_FCM_TOKEN("member_fcm_token", "member_id = :memberId", false),
    SOCIAL_REFRESH_TOKEN("social_refresh_token",
            "social_account_id IN (SELECT id FROM social_account WHERE member_id = :memberId)", false),
    SOCIAL_ACCOUNT("social_account", "member_id = :memberId", false),
    LOCAL_ACCOUNT("local_account", "member_id = :memberId", false),
    FAMILY_MEMBERSHIP("family_membership", "guardian_id = :memberId", false),
    /** 탈퇴 어르신이 본 사람의 기록. 탈퇴 보호자가 본 기록(viewer)은 어르신의 열람 확인 자료라 남긴다. */
    LOCATION_ACCESS_LOG("location_access_log", "senior_member_id = :memberId", false),
    POINT_HISTORY("point_history",
            "senior_profile_id IN (SELECT id FROM senior_profile WHERE member_id = :memberId)", false),
    SENIOR_PROFILE("senior_profile", "member_id = :memberId", false),

    // 연구 자료(ADR-0043 결정 4). 연구 자료 삭제를 마치지 않은 실증 참가자면 지우지 않고 제외 건수로 센다.
    FOLLOWUP_ANSWER("followup_answer",
            "card_id IN (SELECT id FROM followup_card WHERE senior_id = :memberId)", true),
    FOLLOWUP_CARD("followup_card", "senior_id = :memberId", true),
    LABEL_ANNOTATION("label_annotation",
            "label_id IN (SELECT l.id FROM incident_label l JOIN incident i ON l.incident_ref = i.incident_ref"
                    + " WHERE i.member_id = :memberId)", true),
    INCIDENT_LABEL("incident_label",
            "incident_ref IN (SELECT incident_ref FROM incident WHERE member_id = :memberId)", true),
    INCIDENT_RESPONSE_ATTEMPT("incident_response_attempt",
            "incident_id IN (SELECT incident_id FROM incident WHERE member_id = :memberId)", true),
    INCIDENT_GUARDIAN_RESPONSE("incident_guardian_response",
            "incident_id IN (SELECT incident_id FROM incident WHERE member_id = :memberId)", true),
    INCIDENT("incident", "member_id = :memberId", true),
    DECISION_RECORD("decision_record", "member_id = :memberId", true),
    HEART_RATE_EVENT("heart_rate_event", "member_id = :memberId", true),
    HEART_RATE_EMERGENCY("heart_rate_emergency", "member_id = :memberId", true),
    DEVICE_HEARTBEAT("device_heartbeat", "member_id = :memberId", true),
    DEVICE_EVENT("device_event", "member_id = :memberId", true),
    ACTIVITY_WINDOW("activity_window", "member_id = :memberId", true),
    SENSOR_DROPPED_RANGE("sensor_dropped_range", "member_id = :memberId", true),
    SENSOR_BATCH_REJECTION("sensor_batch_rejection", "member_id = :memberId", true),
    LOCATION_FIX("location_fix", "member_id = :memberId", true),
    SENSOR_BATCH("sensor_batch", "member_id = :memberId", true);

    private final String table;
    private final String condition;
    private final boolean researchData;

    WithdrawnMemberPurgeStep(String table, String condition, boolean researchData) {
        this.table = table;
        this.condition = condition;
        this.researchData = researchData;
    }

    public String table() {
        return table;
    }

    public String condition() {
        return condition;
    }

    public boolean researchData() {
        return researchData;
    }

    /** {@code data_purge_log.target}에 남기는 이름. 같은 표를 두 단계로 지우는 댓글도 갈린다. */
    public String logTarget() {
        return "withdrawn." + name().toLowerCase();
    }
}
