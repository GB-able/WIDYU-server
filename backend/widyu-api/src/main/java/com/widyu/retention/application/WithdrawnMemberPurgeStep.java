package com.widyu.retention.application;

/**
 * 탈퇴 30일 뒤 지우는 회원 자료와 그 순서(LLD-0187 4.1). FK를 가리키는 쪽부터 지운다.
 *
 * <p>조건은 이 상수에서만 SQL에 들어간다. {@code :memberId}가 탈퇴 회원이다. 남기는 것은 회원 행(묘비), 결제, 동의,
 * 관리자 접속기록과 업무 기록, 보호자가 열람자인 위치 열람 기록, 처리 중인 S3 삭제 작업 큐뿐이다
 * (2026-11-23 기획자 답변, ADR-0043 결정 7).
 */
public enum WithdrawnMemberPurgeStep {

    // 탈퇴자가 올린 앨범과 딸린 자료(2026-11-23 사용자 결정: 모두 삭제). 답글이 원댓글을 가리키므로 답글부터.
    ALBUM_COMMENT_REPLY("album_comment",
            "parent_comment_id IS NOT NULL AND album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    // 답글은 앞 단계 몫이다. 원댓글로 한정해야 단계별 지운 건수가 겹치지 않는다.
    ALBUM_COMMENT("album_comment",
            "parent_comment_id IS NULL AND album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM_LIKE("album_like", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM_VIEW("album_view", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM_UNLOCK("album_unlock", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM_DURATION("album_duration", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM_MEDIA_URL("album_media_url", "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM_THUMBNAIL_URL("album_thumbnail_url",
            "album_id IN (SELECT album_id FROM album WHERE member_id = :memberId)"),
    ALBUM("album", "member_id = :memberId"),

    MEDICATION_PROOF_IMAGES("medication_proof_images",
            "medication_proof_id IN (SELECT id FROM medication_proof WHERE member_id = :memberId)"),
    MEDICATION_PROOF("medication_proof", "member_id = :memberId"),
    MEDICINE_SCHEDULE_DETAIL("medicine_schedule_detail",
            "medicine_category_id IN (SELECT c.id FROM medicine_category c JOIN medicine_schedule s"
                    + " ON c.medicine_schedule_id = s.id WHERE s.member_id = :memberId)"),
    MEDICINE_CATEGORY("medicine_category",
            "medicine_schedule_id IN (SELECT id FROM medicine_schedule WHERE member_id = :memberId)"),
    MEDICINE_SCHEDULE("medicine_schedule", "member_id = :memberId"),
    HEALTH_SCHEDULE("health_schedule", "member_id = :memberId"),
    WALK("walk", "member_id = :memberId"),
    ADDRESS_BOOKMARK("address_bookmark", "member_id = :memberId"),
    PARENT_LOCATION("parent_location", "member_id = :memberId"),
    SENIOR_STAY_STATE("senior_stay_state", "member_id = :memberId"),
    MEMBER_NOTIFICATION_SETTING("member_notification_setting", "member_id = :memberId"),

    // 발송 대기가 알림센터 행과 기기 토큰을 가리키므로 먼저 지운다.
    FCM_OUTBOX("fcm_outbox",
            "recipient_member_id = :memberId"
                    + " OR member_fcm_token_id IN (SELECT id FROM member_fcm_token WHERE member_id = :memberId)"
                    + " OR notification_id IN (SELECT id FROM fcm_notification WHERE recipient_member_id = :memberId)"),
    FCM_NOTIFICATION("fcm_notification",
            "recipient_member_id = :memberId"
                    + " OR member_fcm_token_id IN (SELECT id FROM member_fcm_token WHERE member_id = :memberId)"),
    MEMBER_FCM_TOKEN("member_fcm_token", "member_id = :memberId"),
    SOCIAL_REFRESH_TOKEN("social_refresh_token",
            "social_account_id IN (SELECT id FROM social_account WHERE member_id = :memberId)"),
    SOCIAL_ACCOUNT("social_account", "member_id = :memberId"),
    LOCAL_ACCOUNT("local_account", "member_id = :memberId"),
    FAMILY_MEMBERSHIP("family_membership", "guardian_id = :memberId"),
    /** 탈퇴 어르신이 본 사람의 기록. 탈퇴 보호자가 본 기록(viewer)은 어르신의 열람 확인 자료라 남긴다. */
    LOCATION_ACCESS_LOG("location_access_log", "senior_member_id = :memberId"),
    POINT_HISTORY("point_history",
            "senior_profile_id IN (SELECT id FROM senior_profile WHERE member_id = :memberId)"),
    SENIOR_PROFILE("senior_profile", "member_id = :memberId"),

    // 연구 자료. 실증 참가자도 지운다(2026-11-23 기획자 답변).
    FOLLOWUP_ANSWER("followup_answer",
            "card_id IN (SELECT id FROM followup_card WHERE senior_id = :memberId)"),
    FOLLOWUP_CARD("followup_card", "senior_id = :memberId"),
    LABEL_ANNOTATION("label_annotation",
            "label_id IN (SELECT l.id FROM incident_label l JOIN incident i ON l.incident_ref = i.incident_ref"
                    + " WHERE i.member_id = :memberId)"),
    INCIDENT_LABEL("incident_label",
            "incident_ref IN (SELECT incident_ref FROM incident WHERE member_id = :memberId)"),
    INCIDENT_RESPONSE_ATTEMPT("incident_response_attempt",
            "incident_id IN (SELECT incident_id FROM incident WHERE member_id = :memberId)"),
    INCIDENT_GUARDIAN_RESPONSE("incident_guardian_response",
            "incident_id IN (SELECT incident_id FROM incident WHERE member_id = :memberId)"),
    INCIDENT("incident", "member_id = :memberId"),
    DECISION_RECORD("decision_record", "member_id = :memberId"),
    HEART_RATE_EVENT("heart_rate_event", "member_id = :memberId"),
    HEART_RATE_EMERGENCY("heart_rate_emergency", "member_id = :memberId"),
    DEVICE_HEARTBEAT("device_heartbeat", "member_id = :memberId"),
    DEVICE_EVENT("device_event", "member_id = :memberId"),
    ACTIVITY_WINDOW("activity_window", "member_id = :memberId"),
    SENSOR_DROPPED_RANGE("sensor_dropped_range", "member_id = :memberId"),
    SENSOR_BATCH_REJECTION("sensor_batch_rejection", "member_id = :memberId"),
    LOCATION_FIX("location_fix", "member_id = :memberId"),
    SENSOR_BATCH("sensor_batch", "member_id = :memberId"),

    // 실증 회차와 참여 기록. 회차가 참여 기록을, 배정, 마커가 회차를 가리키므로 이 순서다.
    // 기기 단위 시계 매핑(clock_mapping)은 다른 회차와 함께 쓰므로 지우지 않는다.
    RUN_EXPORT("run_export", "run_id IN (SELECT run_id FROM collection_run WHERE member_id = :memberId)"),
    RUN_MARKER("run_marker", "run_id IN (SELECT id FROM collection_run WHERE member_id = :memberId)"),
    RUN_DEVICE_ASSIGNMENT("run_device_assignment",
            "run_id IN (SELECT id FROM collection_run WHERE member_id = :memberId)"),
    COLLECTION_RUN("collection_run", "member_id = :memberId"),
    STUDY_PARTICIPATION_CONSENT("study_participation_consent",
            "study_participation_id IN (SELECT study_participation_id FROM study_participation"
                    + " WHERE member_id = :memberId)"),
    STUDY_PARTICIPATION_HISTORY("study_participation_history",
            "study_participation_id IN (SELECT study_participation_id FROM study_participation"
                    + " WHERE member_id = :memberId)"),
    STUDY_WITHDRAWAL_ITEM("study_participation_withdrawal_item",
            "study_participation_id IN (SELECT study_participation_id FROM study_participation"
                    + " WHERE member_id = :memberId)"),
    STUDY_PARTICIPATION("study_participation", "member_id = :memberId"),

    MEMBER_WITHDRAWAL_REQUEST("member_withdrawal_request", "member_id = :memberId"),
    SOCIAL_UNLINK_TASK("social_unlink_task", "member_id = :memberId");

    private final String table;
    private final String condition;

    WithdrawnMemberPurgeStep(String table, String condition) {
        this.table = table;
        this.condition = condition;
    }

    public String table() {
        return table;
    }

    public String condition() {
        return condition;
    }

    /** {@code data_purge_log.target}(40자)에 남기는 이름. 같은 표를 두 단계로 지우는 댓글도 갈린다. */
    public String logTarget() {
        return "withdrawn." + name().toLowerCase();
    }
}
