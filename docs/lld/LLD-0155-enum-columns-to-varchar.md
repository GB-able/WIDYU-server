# LLD-0155: native ENUM 컬럼과 enum CHECK 제약 정리

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1058 |
| 관련 ADR | - |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

Hibernate 6은 MySQL에서 `@Enumerated(EnumType.STRING)` 필드를 native `ENUM('A','B',...)` 컬럼으로 만든다. 이런 컬럼은 enum 값을 하나 더하면 ALTER 전까지 그 값의 INSERT가 `Data truncated`로 실패한다. 관리자 행위(`alter_admin_audit_log_action.sql`), 알림 카테고리(`alter_fcm_category_*.sql` 2회), 결제 상태(`add_payment_pg_transaction_boundary.sql`)가 이미 값 추가 때문에 ENUM을 다시 정의했다. 공용 `Status`는 7개 테이블이 써서 값 하나에 ALTER 7번이 필요하다.

#672에서 새 enum 컬럼은 `@JdbcTypeCode(SqlTypes.VARCHAR)`로 쓰기로 정했지만 기존 ENUM 23개는 남았다. 또 개발 서버의 `ddl-auto: update`가 VARCHAR enum 컬럼을 만들 때 그 시점 값으로 `CHECK (col in (...))`를 걸어, VARCHAR 컬럼도 새 값을 막는다. 운영 서버가 아직 없어 운영 데이터 변환 없이 정리할 수 있다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain, widyu-api(버전 파일, 테스트), docs
- 23개 필드에 `@JdbcTypeCode(SqlTypes.VARCHAR)`와 `@Column(length = n)`을 단다.
- `V2__enum_columns_to_varchar.sql`: 23개 컬럼을 `MODIFY COLUMN ... VARCHAR(n)`으로 바꾸고 enum 값 CHECK 제약 30개를 `DROP CHECK`로 지운다.
- `FlywayMigrationMySqlTest`에 "버전 파일을 모두 적용하면 native ENUM 컬럼과 enum 값 CHECK가 없다"를 더한다.
- 규칙 문서(`backend/CLAUDE.md`, `backend/AGENTS.md`의 MySQL ENUM 항목, ERD 마이그레이션 이력).

### Out of scope
- enum 값 자체(이름, 추가, 삭제)는 바꾸지 않는다.
- `heart_rate_event` 온라인 변환(새 컬럼 추가, 이중 쓰기, 백필): 운영이 없고 개발 서버 규모를 모르므로 한 번의 `MODIFY`로 한다. 행 수가 크면 배포 전에 다시 판단한다(8절).
- `scripts/mysql`의 옛 ENUM ALTER 스크립트: 지우지 않는다. 이력으로 남긴다.

## 3. 인터페이스 / API

API 변경 없음. 저장되는 문자열 값이 같아 응답도 같다.

## 4. 데이터 모델

### 대상 23개 컬럼

2026-11-14 개발 서버 덤프(= V1)의 ENUM 컬럼 23개와 엔티티 목록(점검 보고서 3절)이 정확히 일치한다. DEFAULT가 있는 컬럼은 없다. NULL 여부는 덤프 그대로 둔다.

| # | 테이블.컬럼 | 엔티티 필드 | 덤프 NULL | 새 타입 | 비고 |
| --- | --- | --- | --- | --- | --- |
| 1 | `admin_audit_log.action` | `AdminAuditLog.action` | NOT NULL | VARCHAR(50) | 최장 값 38자, 기존 `length = 50` 유지 |
| 2 | `fcm_notification.fcm_category` | `FcmNotification.fcmCategory` | NULL | VARCHAR(32) | 덤프 ENUM에 `UNREAD`가 빠져 있다(엔티티에는 있음). VARCHAR로 해소 |
| 3 | `fcm_outbox.fcm_category` | `FcmOutbox.fcmCategory` | NOT NULL | VARCHAR(32) | `create_fcm_outbox.sql`은 VARCHAR(32), `alter_fcm_category_incident_self_check.sql`이 ENUM으로 바꿨고 개발 서버는 ENUM. 원래 VARCHAR(32)로 돌린다 |
| 4 | `fcm_outbox.state` | `FcmOutbox.state` | NOT NULL | VARCHAR(24) | 스크립트는 VARCHAR(24)였으나 개발 서버는 ENUM |
| 5 | `payment.status` | `Payment.status` | NOT NULL | VARCHAR(32) | `WAITING_FOR_DEPOSIT` 19자 |
| 6 | `payment_order.status` | `PaymentOrder.status` | NOT NULL | VARCHAR(20) | |
| 7 | `payment_cancel.status` | `PaymentCancel.status` | NOT NULL | VARCHAR(20) | |
| 8 | `point_history.type` | `PointHistory.type` | NOT NULL | VARCHAR(20) | |
| 9 | `member.role` | `Member.role` | NOT NULL | VARCHAR(20) | |
| 10 | `member.type` | `Member.type` | NOT NULL | VARCHAR(20) | |
| 11 | `member.status` | `Member.status` | NOT NULL | VARCHAR(20) | 공용 `Status` |
| 12 | `heart_rate_event.status` | `HeartRateEvent.status` | NOT NULL | VARCHAR(20) | 큰 테이블(8절) |
| 13 | `health_schedule.progress_status` | `HealthSchedule.progressStatus` | NOT NULL | VARCHAR(20) | |
| 14 | `health_schedule.status` | `HealthSchedule.status` | NOT NULL | VARCHAR(20) | 공용 `Status` |
| 15 | `medicine_schedule.status` | `MedicineSchedule.status` | NOT NULL | VARCHAR(20) | 공용 `Status` |
| 16 | `address_bookmark.status` | `AddressBookmark.status` | NULL | VARCHAR(20) | 공용 `Status` |
| 17 | `album.status` | `Album.status` | NOT NULL | VARCHAR(20) | 공용 `Status` |
| 18 | `album_comment.status` | `AlbumComment.status` | NOT NULL | VARCHAR(20) | 공용 `Status` |
| 19 | `parent_location.location_type` | `ParentLocation.locationType` | NOT NULL | VARCHAR(20) | |
| 20 | `parent_location.status` | `ParentLocation.status` | NULL | VARCHAR(20) | 공용 `Status` |
| 21 | `run_export.status` | `RunExport.status` | NOT NULL | VARCHAR(10) | 기존 `length = 10` 유지(최장 7자) |
| 22 | `collection_run.status` | `CollectionRun.status` | NOT NULL | VARCHAR(10) | 기존 `length = 10` 유지(최장 6자) |
| 23 | `s3_object_deletion_task.status` | `S3ObjectDeletionTask.status` | NOT NULL | VARCHAR(20) | |

길이는 기존 VARCHAR enum 관례(상태 20, 분류 32, 종류 48)를 따르고, 지금 최장 값보다 짧아지지 않게 했다. 엔티티에 길이가 이미 있던 필드(1, 3, 4, 21, 22)는 그 값을 쓴다.

### 지우는 enum 값 CHECK 제약 30개

개발 서버(V1)에 있는 `<table>_chk_N` 30개다. 모두 VARCHAR enum 컬럼의 `IN (...)` 목록이며, 이름은 V1과 같다: `activity_window`(1), `consent_record`(2), `followup_answer`(2), `followup_card`(1), `incident`(4), `incident_guardian_response`(1), `incident_label`(3), `incident_response_attempt`(3), `label_annotation`(3), `location_access_log`(1), `member_withdrawal_request`(1), `sensor_batch`(1), `sensor_batch_rejection`(1), `social_unlink_task`(1), `study_participation`(2), `study_participation_history`(3).

CHECK를 남기면 VARCHAR여도 새 enum 값이 막혀 "값 추가에 ALTER가 필요 없다"는 목표를 못 이룬다. 값 검증은 지금처럼 애플리케이션의 enum 변환이 맡는다. 수기 스크립트로 만든 테이블에는 원래 CHECK가 없었다.

## 5. 처리 흐름

1. 앱 기동 시 Flyway가 V2를 적용한다(dev는 #1055로 V1이 baseline 처리된 뒤).
2. 테이블마다 ALTER 한 번으로 묶어 ENUM 컬럼을 바꾼다(`member`는 3개 컬럼을 한 번에). 이어서 CHECK를 지운다.
3. Hibernate validate가 VARCHAR 매핑과 컬럼을 대조한다. V2 없이 새 엔티티로 뜨면 `found [enum], but expecting [varchar(20)]`로 기동이 멈춘다(로컬에서 확인).

## 6. 예외 / 에러 처리

| 상황 | 동작 |
| --- | --- |
| V2 중간 실패(예: 잠금 대기 초과) | MySQL DDL은 문장마다 커밋되므로 앞선 ALTER는 남는다. Flyway 이력에 실패 행이 남아 앱이 뜨지 않는다. 남은 문장을 확인해 손으로 마치거나 되돌린 뒤 `flyway repair`. |
| CHECK 이름이 다른 DB(옛 로컬 DB 등) | `DROP CHECK`가 실패한다. dev와 빈 DB(운영)는 V1과 이름이 같아 해당 없다. 로컬은 DB를 비우고 V1부터 적용한다. |

## 7. 인수조건 (Acceptance Criteria)

- [x] 23개 `@Enumerated(STRING)` 필드가 모두 `@JdbcTypeCode(SqlTypes.VARCHAR)`로 매핑된다. widyu-domain에 `@JdbcTypeCode` 없는 `@Enumerated` 필드가 남지 않는다.
- [x] 빈 MySQL에 V1, V2를 적용하면 Hibernate validate가 통과한다(prod와 같은 조건).
- [x] V2 적용 후 native ENUM 컬럼과 enum 값 CHECK 제약이 0개다.
- [x] NULL 여부는 덤프와 같다(NULL 3개: #2, #16, #20).
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- **선행**: #1055(Flyway, V1)가 먼저 머지되어야 한다.
- **개발 서버 DB 쓰기**: 머지 후 첫 배포에서 V2가 실행된다. 23개 컬럼 변경은 MySQL 8.0에서 `ALGORITHM=INPLACE`가 안 돼(로컬에서 `Cannot change column type INPLACE` 확인) 테이블 복사로 돌고, 그동안 해당 테이블 쓰기가 막힌다. 앱 기동이 V2 완료를 기다리므로 그 시간만큼 배포가 길어진다. CHECK 삭제는 `ALGORITHM=INSTANT`로 끝난다(로컬 확인).
- **큰 테이블**: `heart_rate_event`(개발 서버 덤프의 AUTO_INCREMENT 값이 약 19.7만. 행 수는 아니다), `fcm_notification`, `fcm_outbox`는 행이 많을 수 있다. 개발 서버 행 수를 측정하지 않았으므로 배포 전에 `SELECT COUNT(*)`로 확인한다. 수백만 행이면 배포를 트래픽 적은 시간에 하거나 `heart_rate_event`만 온라인 방식으로 분리한다.
- **값 호환**: ENUM → VARCHAR는 저장된 문자열을 그대로 옮긴다. 쿼리의 `status = 'ACTIVE'` 비교는 같게 동작한다. ENUM 내부 순번으로 정렬하던 쿼리는 문자열 정렬로 바뀌는데, 이 23개 컬럼을 `ORDER BY`에 쓰는 곳은 없다(리포지토리 검색).
- **롤백**: 이전 이미지는 `@Enumerated`만 있어도 VARCHAR 컬럼에 문자열을 쓰고 읽을 수 있다. 단 이전 이미지는 dev에서 `ddl-auto: update`라 ENUM으로 되돌리지 않는다.

## 9. 미결정 사항 (Open Questions)

- [ ] 개발 서버 변환 시점(이슈의 정책 확인 항목): 기본값으로 "이 PR 머지 후 첫 개발 서버 배포에서 Flyway가 자동 적용"으로 정했다. 확인 필요. 배포 전 행 수 확인 결과에 따라 시간대를 정한다.

## 10. 참고

- 2026-11-14 저장소 점검 DB 보고서 [중간-6], 3절 enum 매핑 규칙 위반 목록
- #672 enum 컬럼 매핑 규칙
- LLD-0154 Flyway 도입과 기준 스키마
