-- 기준 스키마(LLD-0154). 2026-11-14 개발 서버 DB(MySQL 8.0.46, develop 513f5396 배포 상태)의
-- mysqldump --no-data 결과에서 AUTO_INCREMENT 시작값과 mysqldump 세션 설정을 지웠다. 그 밖의 정의는 개발 서버와 같다.
-- 개발 서버처럼 이미 테이블이 있는 DB는 이 파일을 실행하지 않고 버전 1로만 표시한다(baseline-on-migrate).
-- 테이블이 이름 순서라 아직 없는 테이블을 가리키는 FK가 있어 FK 검사를 끄고 만든다.
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `activity_window` (
  `activity_window_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `magnitude_sq_sum_mg` bigint NOT NULL,
  `magnitude_sum_mg` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `sample_count` int NOT NULL,
  `state` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `window_end_ms` bigint NOT NULL,
  `window_start_ms` bigint NOT NULL,
  PRIMARY KEY (`activity_window_id`),
  UNIQUE KEY `uk_activity_window_member_start` (`member_id`,`window_start_ms`),
  CONSTRAINT `activity_window_chk_1` CHECK ((`state` in (_utf8mb4'REST',_utf8mb4'LOW',_utf8mb4'ACTIVE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `address_bookmark` (
  `address_bookmark_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `jibun` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `road` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `road_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  PRIMARY KEY (`address_bookmark_id`),
  KEY `FKa7pjhejyjbs83y602econi4t7` (`member_id`),
  CONSTRAINT `FKa7pjhejyjbs83y602econi4t7` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_access_log` (
  `admin_access_log_id` bigint NOT NULL AUTO_INCREMENT,
  `accessed_at` datetime(6) NOT NULL,
  `admin_id` bigint NOT NULL,
  `admin_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `client_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `method` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `path` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `query` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` int NOT NULL,
  `target_member_id` bigint DEFAULT NULL,
  `target_ref` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`admin_access_log_id`),
  KEY `idx_admin_access_log_admin_time` (`admin_id`,`accessed_at`),
  KEY `idx_admin_access_log_time` (`accessed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_audit_log` (
  `audit_log_id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('ADMIN_LOGIN','COLLECTION_RUN_CLOSE','COLLECTION_RUN_OPEN','FCM_TEST_SEND','MEMBER_STATUS_CHANGE','MEMBER_WITHDRAWAL_REQUEST_CANCELLED','MEMBER_WITHDRAWAL_REQUEST_PROCESSED','STUDY_PARTICIPATION_DELETION_PROCESSED','STUDY_PARTICIPATION_PERIOD_CHANGE','STUDY_PARTICIPATION_REGISTER','STUDY_PARTICIPATION_WITHDRAW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `admin_id` bigint NOT NULL,
  `admin_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `detail` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_id` bigint DEFAULT NULL,
  `target_type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`audit_log_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album` (
  `album_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `comment_count` int NOT NULL,
  `content` varchar(2200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `like_count` int NOT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `view_count` int NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`album_id`),
  KEY `idx_album_status_created_id` (`status`,`created_at` DESC,`album_id` DESC),
  KEY `FKt93wvvlvv9dqhnmdd2a955mau` (`member_id`),
  CONSTRAINT `FKt93wvvlvv9dqhnmdd2a955mau` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `depth` int NOT NULL,
  `like_count` int NOT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `album_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `parent_comment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`comment_id`),
  KEY `FK1cy6btdqoy9096m0frrcpo3g2` (`album_id`),
  KEY `FKq6o4qb669fd7fcq9b7q0grto2` (`member_id`),
  KEY `FK9x2uo0uok17mu3eyuoxvcbofy` (`parent_comment_id`),
  CONSTRAINT `FK1cy6btdqoy9096m0frrcpo3g2` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`),
  CONSTRAINT `FK9x2uo0uok17mu3eyuoxvcbofy` FOREIGN KEY (`parent_comment_id`) REFERENCES `album_comment` (`comment_id`),
  CONSTRAINT `FKq6o4qb669fd7fcq9b7q0grto2` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_duration` (
  `album_id` bigint NOT NULL,
  `duration` int DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`album_id`,`display_order`),
  CONSTRAINT `FK6wg3yjxkdg535l2c8x8pxq9n9` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_like` (
  `like_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `album_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`like_id`),
  UNIQUE KEY `uk_album_likes_album_member` (`album_id`,`member_id`),
  KEY `FKir72jrdsylmwlhncl9hp2w87a` (`member_id`),
  CONSTRAINT `FKir72jrdsylmwlhncl9hp2w87a` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKr70ri4l53wpi82nkxo3ynn7f3` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_media_url` (
  `album_id` bigint NOT NULL,
  `media_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`album_id`,`display_order`),
  CONSTRAINT `FKsjrqm489ry8wbg1ostucrkmdv` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_thumbnail_url` (
  `album_id` bigint NOT NULL,
  `thumbnail_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`album_id`,`display_order`),
  CONSTRAINT `FKp4vn89kq7u9lqq7odp98yy732` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_unlock` (
  `unlock_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `unlocked_at` datetime(6) NOT NULL,
  `album_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`unlock_id`),
  UNIQUE KEY `uk_album_unlocks_album_member` (`album_id`,`member_id`),
  KEY `FKjdqsom4lhdm1a9h62k7f8k0ut` (`member_id`),
  CONSTRAINT `FKjdqsom4lhdm1a9h62k7f8k0ut` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKr55n9p9lsh55mlrclfej6n5by` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `album_view` (
  `view_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `view_count` int NOT NULL,
  `album_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`view_id`),
  UNIQUE KEY `uk_album_views_album_member` (`album_id`,`member_id`),
  KEY `FKsiq6q73hu6of9si534p7fklor` (`member_id`),
  CONSTRAINT `FK9k1v839ojictli0w227m8oud7` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`),
  CONSTRAINT `FKsiq6q73hu6of9si534p7fklor` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `clock_mapping` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `anchor_elapsed_ns` bigint NOT NULL,
  `anchor_epoch_ms` bigint NOT NULL,
  `boot_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `clock_mapping_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `first_seen_at_ms` bigint NOT NULL,
  `last_seen_at_ms` bigint NOT NULL,
  `observed_max_elapsed_ns` bigint NOT NULL,
  `observed_min_elapsed_ns` bigint NOT NULL,
  `uncertainty_ms` double NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_clock_mapping_id` (`clock_mapping_id`),
  KEY `idx_clock_mapping_device` (`device_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `collection_run` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `acceptance_receipt_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `collection_mode` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `consent_version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `data_policy` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ended_at_ms` bigint DEFAULT NULL,
  `identified_until` date DEFAULT NULL,
  `missing_reason` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `open_marker` int DEFAULT NULL,
  `participation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `protocol_ref` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pseudonymized_at` date DEFAULT NULL,
  `quality_notes` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `research_until` date DEFAULT NULL,
  `run_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at_ms` bigint NOT NULL,
  `status` enum('CLOSED','OPEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `study_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `study_participation_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_collection_run_run_id` (`run_id`),
  UNIQUE KEY `uk_collection_run_member_open` (`member_id`,`open_marker`),
  KEY `idx_collection_run_member_status` (`member_id`,`status`),
  KEY `FK7nhhtyvkwhelpp24h0345p4be` (`study_participation_id`),
  CONSTRAINT `FK7i0f10h20c9y1xnf41jx5ksgs` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FK7nhhtyvkwhelpp24h0345p4be` FOREIGN KEY (`study_participation_id`) REFERENCES `study_participation` (`study_participation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `consent_record` (
  `consent_record_id` bigint NOT NULL AUTO_INCREMENT,
  `consent_key` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `granted` bit(1) NOT NULL,
  `member_id` bigint NOT NULL,
  `recorded_at` datetime(6) NOT NULL,
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`consent_record_id`),
  KEY `idx_consent_record_member_key_time` (`member_id`,`consent_key`,`recorded_at`),
  CONSTRAINT `consent_record_chk_1` CHECK ((`consent_key` in (_utf8mb4'PRIVACY_PERSONAL',_utf8mb4'PRIVACY_HEALTH',_utf8mb4'LOCATION',_utf8mb4'GUARDIAN_LOCATION_PROVIDE',_utf8mb4'LOCATION_NOTICE_BATCHED',_utf8mb4'RETENTION_NOTICE'))),
  CONSTRAINT `consent_record_chk_2` CHECK ((`source` in (_utf8mb4'APP',_utf8mb4'ADMIN')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `data_purge_log` (
  `data_purge_log_id` bigint NOT NULL AUTO_INCREMENT,
  `cutoff_at` datetime(6) NOT NULL,
  `dry_run` bit(1) NOT NULL,
  `error_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `exempt_rows` bigint DEFAULT NULL,
  `finished_at` datetime(6) NOT NULL,
  `run_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `target` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_rows` bigint DEFAULT NULL,
  PRIMARY KEY (`data_purge_log_id`),
  KEY `idx_data_purge_log_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `decision_record` (
  `decision_record_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `alert_at_ms` bigint DEFAULT NULL,
  `alert_delivered` bit(1) NOT NULL,
  `alert_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decider_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decider_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision_at_ms` bigint NOT NULL,
  `decision_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `decision_output` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `feature_support_end_ms` bigint NOT NULL,
  `input_cutoff_ms` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `model_available_at_server_max_ms` bigint NOT NULL,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stream_ids_used` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_batch_id` varchar(26) COLLATE utf8mb4_unicode_ci NOT NULL,
  `trigger_path` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `window_end_ms` bigint NOT NULL,
  `window_start_ms` bigint NOT NULL,
  `hr_accuracy` varchar(12) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `hr_bpm` int DEFAULT NULL,
  `hr_measured_at_ms` bigint DEFAULT NULL,
  `reason` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`decision_record_id`),
  UNIQUE KEY `uk_decision_record_decision_id` (`decision_id`),
  KEY `idx_decision_record_run_time` (`run_id`,`decision_at_ms`),
  KEY `idx_decision_record_member_time` (`member_id`,`decision_at_ms`),
  KEY `idx_decision_record_trigger_batch` (`trigger_batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `device_event` (
  `device_event_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `attributes` text COLLATE utf8mb4_unicode_ci,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `occurred_at_ms` bigint NOT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_sha256` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `server_received_at_ms` bigint NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`device_event_id`),
  UNIQUE KEY `uk_device_event` (`device_id`,`event_id`),
  KEY `idx_device_event_member_time` (`member_id`,`occurred_at_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `device_heartbeat` (
  `device_heartbeat_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `accepted_at_ms` bigint NOT NULL,
  `background_restricted` bit(1) DEFAULT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `hr_session` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_hr_ts_ms` bigint DEFAULT NULL,
  `last_imu_ts_ms` bigint DEFAULT NULL,
  `location_permission` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `participation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_sha256` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `persisted_at_ms` bigint NOT NULL,
  `phone_app_ver` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone_battery_pct` int NOT NULL,
  `phone_charging` bit(1) DEFAULT NULL,
  `phone_os` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone_queue_depth` int NOT NULL,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `server_received_at_ms` bigint NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `socket_connected` bit(1) NOT NULL,
  `study_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ts_ms` bigint NOT NULL,
  `watch_app_ver` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `watch_battery_pct` int DEFAULT NULL,
  `watch_connected` bit(1) NOT NULL,
  `watch_device_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `watch_on_body` bit(1) DEFAULT NULL,
  `watch_queue_depth` int DEFAULT NULL,
  PRIMARY KEY (`device_heartbeat_id`),
  UNIQUE KEY `uk_device_heartbeat_ts` (`device_id`,`session_id`,`ts_ms`),
  KEY `idx_device_heartbeat_member_time` (`member_id`,`ts_ms`),
  KEY `idx_device_heartbeat_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `family` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `family_code` varchar(6) COLLATE utf8mb4_unicode_ci NOT NULL,
  `family_order_revision` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3typenyk8c8s84g067oifa11` (`family_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `family_membership` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `connected_at` datetime(6) NOT NULL,
  `is_leader` bit(1) NOT NULL,
  `is_representative` bit(1) NOT NULL,
  `nickname` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `family_id` bigint NOT NULL,
  `guardian_id` bigint NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKs2f59cuty1v1gkgu7m05ok1y0` (`guardian_id`),
  KEY `FK2r3uqw8xskfk04na0n0xtj0j7` (`family_id`),
  CONSTRAINT `FK2r3uqw8xskfk04na0n0xtj0j7` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`),
  CONSTRAINT `FK79l4x38u05y5q8okxnaw82blf` FOREIGN KEY (`guardian_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `fcm_notification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `body` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fcm_category` enum('ALBUM','ALL','ETC','HEALTH_SCHEDULE','HEART_MESSAGE','MEDICINE_SCHEDULE','SAFE_ZONE','TARGET','WALK','INCIDENT_SELF_CHECK','LOCATION_NOTICE') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_read` bit(1) NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_fcm_token_id` bigint DEFAULT NULL,
  `recipient_member_id` bigint DEFAULT NULL,
  `decision_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `actor_display_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deep_link` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `event_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `push_eligible` bit(1) DEFAULT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `remaining_locked_count` int DEFAULT NULL,
  `retention_policy_version` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `senior_display_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `senior_id` bigint DEFAULT NULL,
  `type` varchar(48) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_fcm_notification_recipient_event` (`recipient_member_id`,`event_id`),
  KEY `FK3croul1hadbpb8rdf1iy8gpom` (`member_fcm_token_id`),
  CONSTRAINT `FK3croul1hadbpb8rdf1iy8gpom` FOREIGN KEY (`member_fcm_token_id`) REFERENCES `member_fcm_token` (`id`),
  CONSTRAINT `FKhc1lu772b9ivlmul2hqwae19m` FOREIGN KEY (`recipient_member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `fcm_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `attempts` int NOT NULL,
  `available_at` datetime(6) NOT NULL,
  `body` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency` bit(1) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `family_id` bigint DEFAULT NULL,
  `fcm_category` enum('ALBUM','ALL','ETC','HEALTH_SCHEDULE','HEART_MESSAGE','INCIDENT_SELF_CHECK','LOCATION_NOTICE','MEDICINE_SCHEDULE','SAFE_ZONE','TARGET','UNREAD','WALK') COLLATE utf8mb4_unicode_ci NOT NULL,
  `fence` bigint NOT NULL,
  `image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `related_member_id` bigint DEFAULT NULL,
  `scheme` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` enum('CANCELLED','CLAIMED','EXHAUSTED','EXPIRED','PENDING','SENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_fcm_token_id` bigint NOT NULL,
  `recipient_member_id` bigint NOT NULL,
  `data_revision` bigint DEFAULT NULL,
  `data_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `decision_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `data_payload` text COLLATE utf8mb4_unicode_ci,
  `notification_id` bigint DEFAULT NULL,
  `notification_type` varchar(48) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_fcm_outbox_due` (`state`,`available_at`,`lease_until`),
  KEY `FKv1osssgl7mxxujotg0pb2khc` (`member_fcm_token_id`),
  KEY `FK64lqdmqa4ssl1u9f6fowmjh8o` (`recipient_member_id`),
  CONSTRAINT `FK64lqdmqa4ssl1u9f6fowmjh8o` FOREIGN KEY (`recipient_member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKv1osssgl7mxxujotg0pb2khc` FOREIGN KEY (`member_fcm_token_id`) REFERENCES `member_fcm_token` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `followup_answer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `device_submitted_at_ms` bigint NOT NULL,
  `q1` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `q2` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `q3` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `question_set_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `server_received_at_ms` bigint NOT NULL,
  `card_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_followup_answer_card` (`card_id`),
  CONSTRAINT `FKdwlddfjj01sooe9c4ci2edshi` FOREIGN KEY (`card_id`) REFERENCES `followup_card` (`id`),
  CONSTRAINT `followup_answer_chk_1` CHECK ((`q1` in (_utf8mb4'YES',_utf8mb4'NO',_utf8mb4'DONT_KNOW',_utf8mb4'REFUSE'))),
  CONSTRAINT `followup_answer_chk_2` CHECK ((`q2` in (_utf8mb4'NEEDED',_utf8mb4'NOT_NEEDED',_utf8mb4'DONT_KNOW',_utf8mb4'REFUSE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `followup_card` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `event_at_ms` bigint NOT NULL,
  `expires_at_ms` bigint NOT NULL,
  `incident_ref` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `issued_at_ms` bigint NOT NULL,
  `question_set_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `senior_id` bigint NOT NULL,
  `state` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `visit_key` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_followup_card_incident` (`incident_ref`),
  UNIQUE KEY `uk_followup_card_visit` (`senior_id`,`visit_key`),
  KEY `idx_followup_card_senior_state_expiry` (`senior_id`,`state`,`expires_at_ms`),
  KEY `idx_followup_card_expiry` (`state`,`expires_at_ms`,`id`),
  CONSTRAINT `followup_card_chk_1` CHECK ((`state` in (_utf8mb4'ISSUED',_utf8mb4'ANSWERED',_utf8mb4'DECLINED',_utf8mb4'EXPIRED_NO_RESPONSE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `health_schedule` (
  `health_schedule_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `is_reward` bit(1) NOT NULL,
  `latitude` double DEFAULT NULL,
  `longitude` double DEFAULT NULL,
  `place_address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `progress_status` enum('COMPLETED','INCOMPLETE','UPCOMING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `reward_point` int NOT NULL,
  `schedule_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scheduled_at` datetime(6) NOT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`health_schedule_id`),
  KEY `FKomqx9elmjgk2g9qkwkt489254` (`member_id`),
  CONSTRAINT `FKomqx9elmjgk2g9qkwkt489254` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `heart_rate_emergency` (
  `heart_rate_emergency_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `heart_rate` int NOT NULL,
  `location` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `measured_at` datetime(6) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`heart_rate_emergency_id`),
  UNIQUE KEY `uk_heart_emergency_member_time` (`member_id`,`measured_at`),
  CONSTRAINT `FKjyqfrfqd0y7qrxtcqtdw2l5sh` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `heart_rate_event` (
  `heart_rate_event_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `heart_rate` int NOT NULL,
  `measured_at` datetime(6) NOT NULL,
  `status` enum('ANOMALY','CAUTION','EMERGENCY','NORMAL','UNKNOWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `accuracy` varchar(12) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_id` varchar(26) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`heart_rate_event_id`),
  UNIQUE KEY `uk_heart_event_member_time` (`member_id`,`measured_at`),
  CONSTRAINT `FKbuby21k3on348kc89fqqv54l5` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `incident` (
  `incident_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `decision_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `emergency_called_at_ms` bigint DEFAULT NULL,
  `incident_ref` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `level` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `opened_at_ms` bigint NOT NULL,
  `outcome` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `resolved_at_ms` bigint DEFAULT NULL,
  `resolved_by` bigint DEFAULT NULL,
  `respond_by_ms` bigint NOT NULL,
  `responded_at_ms` bigint DEFAULT NULL,
  `response` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `response_via` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `state` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `detection_count` int NOT NULL,
  `device_responded_at_ms` bigint DEFAULT NULL,
  `guardian_response_at_ms` bigint DEFAULT NULL,
  `guardian_response_by` bigint DEFAULT NULL,
  `guardian_response_type` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `initial_alert_sent_at_ms` bigint DEFAULT NULL,
  `last_decision_id` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_detected_at_ms` bigint DEFAULT NULL,
  `ok_notice_sent_at_ms` bigint DEFAULT NULL,
  `policy_revision` bigint DEFAULT NULL,
  `situation_ended_at_ms` bigint DEFAULT NULL,
  `second_alert_cancelled_at_ms` bigint DEFAULT NULL,
  `second_alert_due_at_ms` bigint DEFAULT NULL,
  `second_alert_sent_at_ms` bigint DEFAULT NULL,
  `prompt_displayed_at_ms` bigint DEFAULT NULL,
  `prompt_displayed_via` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`incident_id`),
  UNIQUE KEY `uk_incident_incident_ref` (`incident_ref`),
  UNIQUE KEY `uk_incident_decision_id` (`decision_id`),
  KEY `idx_incident_member_time` (`member_id`,`opened_at_ms`),
  KEY `idx_incident_run_time` (`run_id`,`opened_at_ms`),
  KEY `idx_incident_state_deadline` (`state`,`respond_by_ms`),
  KEY `idx_incident_alert_pending` (`initial_alert_sent_at_ms`,`respond_by_ms`),
  KEY `idx_incident_second_alert_pending` (`second_alert_sent_at_ms`,`second_alert_due_at_ms`),
  CONSTRAINT `incident_chk_2` CHECK ((`outcome` in (_utf8mb4'TRUE_EMERGENCY',_utf8mb4'FALSE_ALARM',_utf8mb4'UNKNOWN'))),
  CONSTRAINT `incident_chk_3` CHECK ((`response` in (_utf8mb4'OK',_utf8mb4'HELP'))),
  CONSTRAINT `incident_chk_4` CHECK ((`response_via` in (_utf8mb4'WATCH',_utf8mb4'PHONE'))),
  CONSTRAINT `incident_chk_5` CHECK ((`prompt_displayed_via` in (_utf8mb4'WATCH',_utf8mb4'PHONE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `incident_guardian_response` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `guardian_member_id` bigint NOT NULL,
  `incident_id` bigint NOT NULL,
  `responded_at_ms` bigint NOT NULL,
  `response_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_incident_guardian_response_kind` (`incident_id`,`guardian_member_id`,`response_type`),
  CONSTRAINT `incident_guardian_response_chk_1` CHECK ((`response_type` in (_utf8mb4'ACKNOWLEDGED',_utf8mb4'MESSAGE_SENT',_utf8mb4'CALL_INITIATED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `incident_label` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `current_annotation_id` bigint DEFAULT NULL,
  `event_occurrence` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `help_need` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `incident_ref` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `labeled_at` bigint DEFAULT NULL,
  `source` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_incident_label_ref` (`incident_ref`),
  CONSTRAINT `incident_label_chk_1` CHECK ((`event_occurrence` in (_utf8mb4'PRESENT',_utf8mb4'ABSENT',_utf8mb4'UNDETERMINED',_utf8mb4'NOT_ASSESSED'))),
  CONSTRAINT `incident_label_chk_2` CHECK ((`help_need` in (_utf8mb4'NEEDED',_utf8mb4'NOT_NEEDED',_utf8mb4'UNDETERMINED',_utf8mb4'NOT_ASSESSED'))),
  CONSTRAINT `incident_label_chk_3` CHECK ((`source` in (_utf8mb4'HUMAN',_utf8mb4'LLM_JUDGE',_utf8mb4'UNREVIEWED',_utf8mb4'LEGACY_UNREVIEWED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `incident_response_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `device_responded_at_ms` bigint DEFAULT NULL,
  `incident_id` bigint NOT NULL,
  `outcome` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `received_at_ms` bigint NOT NULL,
  `response` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_via` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_incident_response_attempt_incident` (`incident_id`),
  CONSTRAINT `incident_response_attempt_chk_1` CHECK ((`outcome` in (_utf8mb4'ACCEPTED',_utf8mb4'LATE',_utf8mb4'DUPLICATE'))),
  CONSTRAINT `incident_response_attempt_chk_2` CHECK ((`response` in (_utf8mb4'OK',_utf8mb4'HELP'))),
  CONSTRAINT `incident_response_attempt_chk_3` CHECK ((`response_via` in (_utf8mb4'WATCH',_utf8mb4'PHONE')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `label_annotation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `annotator_ref` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `annotator_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_occurrence` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `help_need` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `note` text COLLATE utf8mb4_unicode_ci,
  `reviewer_id` bigint DEFAULT NULL,
  `revision` int NOT NULL,
  `rubric_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_evidence_ref` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `supersedes_annotation_id` bigint DEFAULT NULL,
  `label_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_label_annotation_revision` (`label_id`,`revision`),
  KEY `idx_label_annotation_reviewer` (`reviewer_id`,`created_at`),
  CONSTRAINT `FK4lsu5eocfbhpucby7fdnmhhju` FOREIGN KEY (`label_id`) REFERENCES `incident_label` (`id`),
  CONSTRAINT `label_annotation_chk_1` CHECK ((`annotator_type` in (_utf8mb4'HUMAN',_utf8mb4'LLM_JUDGE'))),
  CONSTRAINT `label_annotation_chk_2` CHECK ((`event_occurrence` in (_utf8mb4'PRESENT',_utf8mb4'ABSENT',_utf8mb4'UNDETERMINED',_utf8mb4'NOT_ASSESSED'))),
  CONSTRAINT `label_annotation_chk_3` CHECK ((`help_need` in (_utf8mb4'NEEDED',_utf8mb4'NOT_NEEDED',_utf8mb4'UNDETERMINED',_utf8mb4'NOT_ASSESSED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `local_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_first` bit(1) DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_local_member` (`member_id`),
  UNIQUE KEY `uk_local_email` (`email`),
  CONSTRAINT `FKpxsxo4knxysw07niosg8u885i` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `location_access_log` (
  `location_access_log_id` bigint NOT NULL AUTO_INCREMENT,
  `accessed_at` datetime(6) NOT NULL,
  `notified_at` datetime(6) DEFAULT NULL,
  `path` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `senior_member_id` bigint NOT NULL,
  `viewer_member_id` bigint NOT NULL,
  PRIMARY KEY (`location_access_log_id`),
  KEY `idx_location_access_log_senior_time` (`senior_member_id`,`accessed_at`),
  KEY `idx_location_access_log_senior_notified` (`senior_member_id`,`notified_at`),
  CONSTRAINT `location_access_log_chk_1` CHECK ((`path` in (_utf8mb4'REST_LAST',_utf8mb4'REST_TRAIL',_utf8mb4'REST_FAMILY',_utf8mb4'WS_SUBSCRIBE',_utf8mb4'HOME_OUTING')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `location_fix` (
  `location_fix_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `accepted_at_ms` bigint NOT NULL,
  `accuracy_m` double DEFAULT NULL,
  `altitude_m` double DEFAULT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `heading_deg` double DEFAULT NULL,
  `is_mock` bit(1) NOT NULL,
  `lat` double NOT NULL,
  `lon` double NOT NULL,
  `member_id` bigint NOT NULL,
  `participation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_sha256` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `persisted_at_ms` bigint NOT NULL,
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `seq` bigint NOT NULL,
  `server_received_at_ms` bigint NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `speed_accuracy_mps` double DEFAULT NULL,
  `speed_mps` double DEFAULT NULL,
  `study_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ts_ms` bigint NOT NULL,
  PRIMARY KEY (`location_fix_id`),
  UNIQUE KEY `uk_location_fix_seq` (`device_id`,`session_id`,`seq`),
  KEY `idx_location_fix_member_time` (`member_id`,`ts_ms`),
  KEY `idx_location_fix_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medication_proof` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  `verified_date` date NOT NULL,
  `medicine_schedule_id` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_medication_proof_schedule_date` (`medicine_schedule_id`,`verified_date`),
  KEY `idx_medication_proof_member_verified` (`member_id`,`verified_at`),
  KEY `idx_medication_proof_member_verified_date` (`member_id`,`verified_date`),
  CONSTRAINT `FK1ah7oy35a46tby8ejo3cbq53q` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FK4aysoxc6lbxkshmgr0wih254q` FOREIGN KEY (`medicine_schedule_id`) REFERENCES `medicine_schedule` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medication_proof_image_deletion_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `member_id` bigint NOT NULL,
  `object_key` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `retry_count` int NOT NULL,
  `processing_attempt` int NOT NULL DEFAULT '0',
  `lease_expires_at` datetime DEFAULT NULL,
  `next_retry_at` datetime DEFAULT NULL,
  `last_error_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `failed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_proof_image_deletion_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medication_proof_images` (
  `medication_proof_id` bigint NOT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKd6cuw3pymtw3ppe011eevcwfy` (`medication_proof_id`),
  CONSTRAINT `FKd6cuw3pymtw3ppe011eevcwfy` FOREIGN KEY (`medication_proof_id`) REFERENCES `medication_proof` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medicine` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `efcy_qesitm` text COLLATE utf8mb4_unicode_ci,
  `entp_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `item_image` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `item_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `item_seq` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `use_method_qesitm` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKo23go8ob0nlbqj6xg7s48ifkf` (`item_seq`),
  FULLTEXT KEY `ft_medicine_item_name` (`item_name`) WITH PARSER `ngram`
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medicine_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `medicine_schedule_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKbtb3wdpcrjxfcm19aslsrykrs` (`medicine_schedule_id`),
  CONSTRAINT `FKbtb3wdpcrjxfcm19aslsrykrs` FOREIGN KEY (`medicine_schedule_id`) REFERENCES `medicine_schedule` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medicine_schedule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `alarm_time` time(6) NOT NULL,
  `effective_from` date DEFAULT NULL,
  `effective_to` date DEFAULT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9lrkx2d64cb91a0a9wiksigjt` (`member_id`),
  CONSTRAINT `FK9lrkx2d64cb91a0a9wiksigjt` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `medicine_schedule_detail` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `dose` double NOT NULL,
  `medicine_id` bigint NOT NULL,
  `medicine_category_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKd61eiaktx2e11dh0d3ltgny1o` (`medicine_id`),
  KEY `FKmve9g6wj9ay8exlh2l52ubag9` (`medicine_category_id`),
  CONSTRAINT `FKd61eiaktx2e11dh0d3ltgny1o` FOREIGN KEY (`medicine_id`) REFERENCES `medicine` (`id`),
  CONSTRAINT `FKmve9g6wj9ay8exlh2l52ubag9` FOREIGN KEY (`medicine_category_id`) REFERENCES `medicine_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `member` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone_number` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `profile_image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('ADMIN','TEMPORARY','USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('GUARDIAN','SENIOR') COLLATE utf8mb4_unicode_ci NOT NULL,
  `medication_alarm_revision` bigint NOT NULL,
  `notification_policy_revision` bigint NOT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `member_fcm_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `device_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expired_at` datetime(6) DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `registered_at` datetime(6) DEFAULT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt2cr0axccf1pwaghcbm4bykos` (`token`),
  KEY `FK1t32tfi2x0x2xl8te76jm3q2u` (`member_id`),
  CONSTRAINT `FK1t32tfi2x0x2xl8te76jm3q2u` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `member_notification_setting` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8h8llt4c3kr9gnb4e8d6vu5b1` (`member_id`,`category`),
  CONSTRAINT `FKpw35gvx94u42vhw0kh35gf46` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `member_withdrawal_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `member_id` bigint NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `processed_by_admin_id` bigint DEFAULT NULL,
  `requested_at` datetime(6) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_member_withdrawal_request_member` (`member_id`,`status`),
  KEY `idx_member_withdrawal_request_status` (`status`,`id`),
  CONSTRAINT `member_withdrawal_request_chk_1` CHECK ((`status` in (_utf8mb4'REQUESTED',_utf8mb4'PROCESSED',_utf8mb4'CANCELLED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `parent_location` (
  `parent_location_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `latitude` double NOT NULL,
  `location_type` enum('HOME','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `longitude` double NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `place_address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','DELETED','INACTIVE','PROCESSING') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint DEFAULT NULL,
  PRIMARY KEY (`parent_location_id`),
  KEY `FKla8vaa5sajx31cn5x7ivmk9qq` (`member_id`),
  CONSTRAINT `FKla8vaa5sajx31cn5x7ivmk9qq` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `cancel_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `canceled_amount` int NOT NULL,
  `canceled_at` datetime(6) DEFAULT NULL,
  `canceled_point_amount` int NOT NULL,
  `culture_expense` bit(1) NOT NULL,
  `order_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_at` datetime(6) DEFAULT NULL,
  `status` enum('ABORTED','CANCELED','DONE','EXPIRED','IN_PROGRESS','PARTIAL_CANCELED','READY','WAITING_FOR_DEPOSIT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `payment_order_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6vew52c3hm7vwaiwfvt498x3` (`payment_key`),
  UNIQUE KEY `UK4j6h7da5bgs4piy6fmr19k355` (`payment_order_id`),
  KEY `FK4pswry4r5sx6j57cdeulh1hx8` (`member_id`),
  CONSTRAINT `FK2jypg32xmcq0022jgkcrhmbya` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`),
  CONSTRAINT `FK4pswry4r5sx6j57cdeulh1hx8` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_cancel` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `cancel_amount` int NOT NULL,
  `cancel_point_amount` int NOT NULL,
  `cancel_reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `canceled_at` datetime(6) DEFAULT NULL,
  `idempotency_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `last_error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `pg_idempotency_key` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recovery_stopped_at` datetime(6) DEFAULT NULL,
  `requested_by_member_id` bigint NOT NULL,
  `requested_cancel_amount` int DEFAULT NULL,
  `retry_count` int NOT NULL,
  `status` enum('ABORTED','COMPLETED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKonqtfdw32ev50nj33bi1yh7dy` (`pg_idempotency_key`),
  UNIQUE KEY `uk_payment_cancel_payment_idempotency_key` (`payment_id`,`idempotency_key`),
  KEY `idx_payment_cancel_recovery` (`status`,`next_retry_at`),
  CONSTRAINT `FKimm8rfh30xf2b772xl63x43g2` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_card` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `acquirer_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approve_no` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `card_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `installment_plan_months` int NOT NULL DEFAULT '0',
  `is_interest_free` bit(1) NOT NULL,
  `issuer_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `number` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKsqj1ew5ywyu5rppc1ww44nauk` (`payment_id`),
  CONSTRAINT `FK7mubha7js42gb1qe04i2rnrp1` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_easy_pay` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` int NOT NULL,
  `provider` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKf7jhgmeyps9hnlawnb760dilf` (`payment_id`),
  CONSTRAINT `FKpff2malbfa01lf7g3vu39a2sg` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_order` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` int NOT NULL,
  `approval_last_error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approval_next_retry_at` datetime(6) DEFAULT NULL,
  `approval_payment_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approval_pg_idempotency_key` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approval_recovery_stopped_at` datetime(6) DEFAULT NULL,
  `approval_requested_at` datetime(6) DEFAULT NULL,
  `approval_retry_count` int NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `order_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `order_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `package_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `point_amount` int NOT NULL,
  `status` enum('APPROVING','CANCELED','CREATED','EXPIRED','PAID') COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKixv1xrjhx55oxwjhfb68hi5km` (`order_id`),
  KEY `idx_payment_order_approval_recovery` (`status`,`approval_next_retry_at`),
  KEY `FK37qd6jq5ddhg936tugenplai0` (`member_id`),
  CONSTRAINT `FK37qd6jq5ddhg936tugenplai0` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bank_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `settlement_status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKh8h39lf4jrl3cixf1ciq1ji42` (`payment_id`),
  CONSTRAINT `FK42ubuo0v513y680kte15bk7fu` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_virtual_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_number` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `bank_code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `due_date` datetime(6) DEFAULT NULL,
  `expired` bit(1) NOT NULL,
  `payment_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9xqwc8tdfd2bm3eln429e5cpo` (`payment_id`),
  CONSTRAINT `FKh01y2he132k4259sryog9gan1` FOREIGN KEY (`payment_id`) REFERENCES `payment` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `point_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `amount` bigint NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` enum('EARN','USE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `senior_profile_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6ajx9qyuvcak08b76hbt53s6h` (`operation_key`),
  KEY `FKenomxpebul109hmh083kd91mb` (`senior_profile_id`),
  CONSTRAINT `FKenomxpebul109hmh083kd91mb` FOREIGN KEY (`senior_profile_id`) REFERENCES `senior_profile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `run_device_assignment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_marker` int DEFAULT NULL,
  `assigned_at_ms` bigint NOT NULL,
  `assignment_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `unassigned_at_ms` bigint DEFAULT NULL,
  `wear_site` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `run_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_device_assignment_id` (`assignment_id`),
  UNIQUE KEY `uk_run_device_assignment_active` (`device_id`,`active_marker`),
  KEY `idx_run_device_assignment_device` (`device_id`,`unassigned_at_ms`),
  KEY `FKbkc9cn7xvyj3t5g2tst6k6q3l` (`run_id`),
  CONSTRAINT `FKbkc9cn7xvyj3t5g2tst6k6q3l` FOREIGN KEY (`run_id`) REFERENCES `collection_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `run_export` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `bytes` bigint DEFAULT NULL,
  `error_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `export_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `finished_at_ms` bigint DEFAULT NULL,
  `requested_at_ms` bigint NOT NULL,
  `run_id` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `s3_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `server_build` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sha256` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at_ms` bigint DEFAULT NULL,
  `status` enum('DONE','FAILED','QUEUED','RUNNING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_export_export_id` (`export_id`),
  KEY `idx_run_export_run_status` (`run_id`,`status`),
  KEY `idx_run_export_queue` (`status`,`requested_at_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `run_marker` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `clock_mapping_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `label` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `marker_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_elapsed_ns` bigint NOT NULL,
  `ts_ms` bigint NOT NULL,
  `run_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_run_marker_id` (`marker_id`),
  KEY `idx_run_marker_run_time` (`run_id`,`ts_ms`),
  CONSTRAINT `FKrbq4tgsg5vs8725u14mv4aukk` FOREIGN KEY (`run_id`) REFERENCES `collection_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `s3_object_deletion_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `failed_at` datetime(6) DEFAULT NULL,
  `last_error_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `next_retry_at` datetime(6) DEFAULT NULL,
  `object_key` varchar(1024) COLLATE utf8mb4_unicode_ci NOT NULL,
  `processing_attempt` int NOT NULL,
  `retry_count` int NOT NULL,
  `status` enum('COMPLETED','FAILED','PENDING','PROCESSING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_s3_object_deletion_retry` (`status`,`next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `senior_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `birth_date` date NOT NULL,
  `default_walk_goal` int DEFAULT NULL,
  `invite_code` varchar(7) COLLATE utf8mb4_unicode_ci NOT NULL,
  `points` bigint NOT NULL,
  `version` bigint NOT NULL,
  `family_id` bigint DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK77eio0kxpxk9nu5yvbjeimhus` (`member_id`),
  KEY `FKbu5uivkqq8farh35yl1diw8xe` (`family_id`),
  CONSTRAINT `FK6ogebitm9xhw1rnipywbaqgmq` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `FKbu5uivkqq8farh35yl1diw8xe` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sensor_batch` (
  `sensor_batch_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `acc_fs_hz_requested` double DEFAULT NULL,
  `acc_n` int DEFAULT NULL,
  `acc_t0_elapsed_ns` bigint DEFAULT NULL,
  `accepted_at_ms` bigint NOT NULL,
  `anchor_elapsed_ns` bigint NOT NULL,
  `anchor_epoch_ms` bigint NOT NULL,
  `backfill_for` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `batch_id` varchar(26) COLLATE utf8mb4_unicode_ci NOT NULL,
  `boot_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `byte_size` int NOT NULL,
  `clock_mapping_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `collection_mode` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `gyro_backfill` bit(1) NOT NULL,
  `gyro_fs_hz_requested` double DEFAULT NULL,
  `gyro_mode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `gyro_n` int DEFAULT NULL,
  `gyro_t0_elapsed_ns` bigint DEFAULT NULL,
  `is_resend` bit(1) NOT NULL,
  `measured_at_end_ms` bigint NOT NULL,
  `measured_at_start_ms` bigint NOT NULL,
  `missing_reason` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_available_at_server_ms` bigint NOT NULL,
  `on_body` bit(1) NOT NULL,
  `original_batch_id` varchar(26) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_seq` bigint DEFAULT NULL,
  `original_session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `participation_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `persisted_at_ms` bigint NOT NULL,
  `phone_received_at_ms` bigint DEFAULT NULL,
  `quality_status` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resent_at_ms` bigint DEFAULT NULL,
  `run_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `s3_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `seq` bigint NOT NULL,
  `server_received_at_ms` bigint NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `stream` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `study_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_event_elapsed_ns` bigint DEFAULT NULL,
  `trigger_kind` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `trigger_smv_g` double DEFAULT NULL,
  `trigger_ts_ms` bigint DEFAULT NULL,
  `uncertainty_ms` double NOT NULL,
  `watch_battery_pct` int DEFAULT NULL,
  `wear_state` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `config_mismatch` bit(1) NOT NULL,
  `sample_count` int DEFAULT NULL,
  `ingest_channel` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`sensor_batch_id`),
  UNIQUE KEY `uk_sensor_batch_batch_id` (`batch_id`),
  KEY `idx_sensor_batch_member_stream_time` (`member_id`,`stream`,`measured_at_start_ms`),
  KEY `idx_sensor_batch_seq` (`device_id`,`session_id`,`seq`),
  KEY `idx_sensor_batch_run` (`run_id`),
  KEY `idx_sensor_batch_original_batch_id` (`original_batch_id`),
  CONSTRAINT `FKcc81ejirlra87m187jctmkv6l` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `sensor_batch_chk_1` CHECK ((`ingest_channel` in (_utf8mb4'WS',_utf8mb4'REST')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sensor_batch_rejection` (
  `sensor_batch_rejection_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `batch_id` varchar(26) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_code` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ingest_channel` varchar(8) COLLATE utf8mb4_unicode_ci NOT NULL,
  `member_id` bigint NOT NULL,
  `received_at_ms` bigint NOT NULL,
  `seq` bigint DEFAULT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `stream` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`sensor_batch_rejection_id`),
  KEY `idx_sensor_batch_rejection_member_time` (`member_id`,`received_at_ms`),
  CONSTRAINT `sensor_batch_rejection_chk_1` CHECK ((`ingest_channel` in (_utf8mb4'WS',_utf8mb4'REST')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sensor_dropped_range` (
  `sensor_dropped_range_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `from_seq` bigint NOT NULL,
  `member_id` bigint NOT NULL,
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_sha256` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `persisted_at_ms` bigint NOT NULL,
  `reason` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `server_received_at_ms` bigint NOT NULL,
  `session_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `to_seq` bigint NOT NULL,
  `ts_ms` bigint NOT NULL,
  PRIMARY KEY (`sensor_dropped_range_id`),
  UNIQUE KEY `uk_sensor_dropped_range` (`member_id`,`session_id`,`from_seq`),
  KEY `idx_sensor_dropped_range_member_time` (`member_id`,`ts_ms`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `social_account` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_first` bit(1) DEFAULT NULL,
  `oauth_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `refresh_token` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_provider_user` (`provider`,`oauth_id`),
  KEY `FK2bhcb0uq2buq7m4c6ogvo1fx8` (`member_id`),
  CONSTRAINT `FK2bhcb0uq2buq7m4c6ogvo1fx8` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `social_refresh_token` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `social_account_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlqw4mrv24sf0t9jq0e7mgb108` (`social_account_id`),
  CONSTRAINT `FK38lf93bmj0nt9isfnmwvrgn36` FOREIGN KEY (`social_account_id`) REFERENCES `social_account` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `social_unlink_task` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `attempt_count` int NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `failed_at` datetime(6) DEFAULT NULL,
  `last_error_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `member_id` bigint NOT NULL,
  `next_retry_at` datetime(6) NOT NULL,
  `oauth_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `refresh_token` varchar(2048) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_social_unlink_due` (`status`,`next_retry_at`),
  CONSTRAINT `social_unlink_task_chk_1` CHECK ((`status` in (_utf8mb4'PENDING',_utf8mb4'COMPLETED',_utf8mb4'FAILED')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_participation` (
  `study_participation_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `active_key` varchar(1) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `consent_version` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `consented_at` date NOT NULL,
  `data_policy` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deletion_processed_at` datetime(6) DEFAULT NULL,
  `identified_until` date DEFAULT NULL,
  `participation_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `pseudonymized_at` date DEFAULT NULL,
  `research_until` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `study_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `withdrawal_scope` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`study_participation_id`),
  UNIQUE KEY `uk_study_participation_id` (`participation_id`),
  UNIQUE KEY `uk_study_participation_active` (`study_id`,`member_id`,`active_key`),
  KEY `FKjulhhxywxmn9mpu0yqd4j2cut` (`member_id`),
  CONSTRAINT `FKjulhhxywxmn9mpu0yqd4j2cut` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`),
  CONSTRAINT `study_participation_chk_1` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ENDED',_utf8mb4'WITHDRAWN'))),
  CONSTRAINT `study_participation_chk_2` CHECK ((`withdrawal_scope` in (_utf8mb4'ALL',_utf8mb4'SELECTED_CONSENTS')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_participation_consent` (
  `study_participation_id` bigint NOT NULL,
  `granted` bit(1) NOT NULL,
  `consent_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`study_participation_id`,`consent_key`),
  CONSTRAINT `FK570qe0jikrv44p5lyg7fj96mu` FOREIGN KEY (`study_participation_id`) REFERENCES `study_participation` (`study_participation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_participation_history` (
  `study_participation_history_id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `data_policy` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deletion_processed_at` datetime(6) DEFAULT NULL,
  `history_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `identified_until` date DEFAULT NULL,
  `pseudonymized_at` date DEFAULT NULL,
  `research_until` date DEFAULT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `withdrawal_scope` varchar(24) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  `withdrawn_consent_keys` text COLLATE utf8mb4_unicode_ci,
  `study_participation_id` bigint NOT NULL,
  PRIMARY KEY (`study_participation_history_id`),
  KEY `FKcf3ego3rn5y8x0s4a2062txok` (`study_participation_id`),
  CONSTRAINT `FKcf3ego3rn5y8x0s4a2062txok` FOREIGN KEY (`study_participation_id`) REFERENCES `study_participation` (`study_participation_id`),
  CONSTRAINT `study_participation_history_chk_1` CHECK ((`history_type` in (_utf8mb4'REGISTERED',_utf8mb4'RETENTION_CHANGED',_utf8mb4'WITHDRAWN',_utf8mb4'DELETION_PROCESSED'))),
  CONSTRAINT `study_participation_history_chk_2` CHECK ((`status` in (_utf8mb4'ACTIVE',_utf8mb4'ENDED',_utf8mb4'WITHDRAWN'))),
  CONSTRAINT `study_participation_history_chk_3` CHECK ((`withdrawal_scope` in (_utf8mb4'ALL',_utf8mb4'SELECTED_CONSENTS')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `study_participation_withdrawal_item` (
  `study_participation_id` bigint NOT NULL,
  `consent_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKi0kj4kuhrxe2pj1r0mwuovctc` (`study_participation_id`),
  CONSTRAINT `FKi0kj4kuhrxe2pj1r0mwuovctc` FOREIGN KEY (`study_participation_id`) REFERENCES `study_participation` (`study_participation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `walk` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `actual_steps` int NOT NULL,
  `goal_steps` int NOT NULL,
  `rewarded` bit(1) NOT NULL,
  `walk_date` date NOT NULL,
  `member_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmqghdhoepl0axafqdd6l9d845` (`member_id`,`walk_date`),
  CONSTRAINT `FKhj3h76rpdg06abb79n0d22np` FOREIGN KEY (`member_id`) REFERENCES `member` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
