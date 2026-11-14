-- native ENUM으로 남은 enum 컬럼 23개를 VARCHAR로 바꾸고, enum 값 CHECK 제약 31개를 지운다(LLD-0155, #1058).
-- 엔티티는 모두 @Enumerated(STRING) + @JdbcTypeCode(VARCHAR)라, 이후 enum 값을 더해도 ALTER가 필요 없다.
-- NULL 허용 여부는 V1(2026-11-14 개발 서버)의 정의를 그대로 둔다. DEFAULT가 있던 컬럼은 없다.
-- 문자셋과 정렬은 테이블 기본값(utf8mb4, utf8mb4_unicode_ci)을 따르며 V1과 같다.
--
-- 잠금: MySQL 8.0에서 ENUM -> VARCHAR는 INPLACE가 안 돼 테이블 복사(COPY)로 돌고, 그동안 쓰기가 막힌다.
-- 테이블마다 ALTER 한 번으로 묶어 복사를 한 번만 한다. heart_rate_event처럼 큰 테이블은 배포 전 행 수를 확인한다.
-- CHECK 삭제는 메타데이터만 바꾼다(로컬 MySQL 8.0.44에서 ALGORITHM=INSTANT로 확인).

-- 1) native ENUM -> VARCHAR. 길이는 지금 값의 최장 길이에 여유를 둔 값이다.
ALTER TABLE `admin_audit_log` MODIFY COLUMN `action` VARCHAR(50) NOT NULL;

ALTER TABLE `fcm_notification` MODIFY COLUMN `fcm_category` VARCHAR(32) NULL;

ALTER TABLE `fcm_outbox`
    MODIFY COLUMN `fcm_category` VARCHAR(32) NOT NULL,
    MODIFY COLUMN `state` VARCHAR(24) NOT NULL;

ALTER TABLE `payment` MODIFY COLUMN `status` VARCHAR(32) NOT NULL;

ALTER TABLE `payment_order` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `payment_cancel` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `point_history` MODIFY COLUMN `type` VARCHAR(20) NOT NULL;

ALTER TABLE `member`
    MODIFY COLUMN `role` VARCHAR(20) NOT NULL,
    MODIFY COLUMN `type` VARCHAR(20) NOT NULL,
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `heart_rate_event` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `health_schedule`
    MODIFY COLUMN `progress_status` VARCHAR(20) NOT NULL,
    MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `medicine_schedule` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `address_bookmark` MODIFY COLUMN `status` VARCHAR(20) NULL;

ALTER TABLE `album` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `album_comment` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

ALTER TABLE `parent_location`
    MODIFY COLUMN `location_type` VARCHAR(20) NOT NULL,
    MODIFY COLUMN `status` VARCHAR(20) NULL;

ALTER TABLE `run_export` MODIFY COLUMN `status` VARCHAR(10) NOT NULL;

ALTER TABLE `collection_run` MODIFY COLUMN `status` VARCHAR(10) NOT NULL;

ALTER TABLE `s3_object_deletion_task` MODIFY COLUMN `status` VARCHAR(20) NOT NULL;

-- 2) enum 값 CHECK 제약. 개발 서버의 ddl-auto update가 VARCHAR enum 컬럼을 만들 때 그 시점 값으로 건 것이라,
-- 새 enum 값 INSERT를 막는다. 이름은 V1(개발 서버)과 같다.
ALTER TABLE `activity_window` DROP CHECK `activity_window_chk_1`;

ALTER TABLE `consent_record`
    DROP CHECK `consent_record_chk_1`,
    DROP CHECK `consent_record_chk_2`;

ALTER TABLE `followup_answer`
    DROP CHECK `followup_answer_chk_1`,
    DROP CHECK `followup_answer_chk_2`;

ALTER TABLE `followup_card` DROP CHECK `followup_card_chk_1`;

ALTER TABLE `incident`
    DROP CHECK `incident_chk_2`,
    DROP CHECK `incident_chk_3`,
    DROP CHECK `incident_chk_4`,
    DROP CHECK `incident_chk_5`;

ALTER TABLE `incident_guardian_response` DROP CHECK `incident_guardian_response_chk_1`;

ALTER TABLE `incident_label`
    DROP CHECK `incident_label_chk_1`,
    DROP CHECK `incident_label_chk_2`,
    DROP CHECK `incident_label_chk_3`;

ALTER TABLE `incident_response_attempt`
    DROP CHECK `incident_response_attempt_chk_1`,
    DROP CHECK `incident_response_attempt_chk_2`,
    DROP CHECK `incident_response_attempt_chk_3`;

ALTER TABLE `label_annotation`
    DROP CHECK `label_annotation_chk_1`,
    DROP CHECK `label_annotation_chk_2`,
    DROP CHECK `label_annotation_chk_3`;

ALTER TABLE `location_access_log` DROP CHECK `location_access_log_chk_1`;

ALTER TABLE `member_withdrawal_request` DROP CHECK `member_withdrawal_request_chk_1`;

ALTER TABLE `senior_stay_state` DROP CHECK `senior_stay_state_chk_1`;

ALTER TABLE `sensor_batch` DROP CHECK `sensor_batch_chk_1`;

ALTER TABLE `sensor_batch_rejection` DROP CHECK `sensor_batch_rejection_chk_1`;

ALTER TABLE `social_unlink_task` DROP CHECK `social_unlink_task_chk_1`;

ALTER TABLE `study_participation`
    DROP CHECK `study_participation_chk_1`,
    DROP CHECK `study_participation_chk_2`;

ALTER TABLE `study_participation_history`
    DROP CHECK `study_participation_history_chk_1`,
    DROP CHECK `study_participation_history_chk_2`,
    DROP CHECK `study_participation_history_chk_3`;
