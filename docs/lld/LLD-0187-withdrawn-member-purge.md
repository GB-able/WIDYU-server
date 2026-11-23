# LLD-0187: 탈퇴한 지 30일이 지난 회원의 개인 자료와 올린 앨범을 매일 밤 지운다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1158 |
| 관련 ADR | ADR-0043 결정 7(탈퇴 30일 뒤 삭제와 묘비, 2026-11-23 기획자 답변으로 남기는 기록 확정). 결정 4(참가자 제외)와 결정 9(dry-run 먼저)는 이 작업에 적용하지 않는다 |
| 관련 LLD | LLD-0186(어르신 탈퇴 실행, 선행), LLD-0137(탈퇴 시각), LLD-0136(보관 기간 정리, `data_purge_log`), LLD-0030(S3 삭제 작업 큐) |
| 작성자 | Claude |
| 작성일 | 2026-11-23 |

## 1. 목적 / 배경

개인정보 처리방침은 「탈퇴하면 30일 뒤 지운다」고 약속한다(노션 「보관 기간이 지난 자료 지우기와 탈퇴 처리를 처리방침대로 맞추기」 2-2). 지금 탈퇴는 이름과 번호를 가리고 복약 인증 사진, 프로필 사진, Redis 실시간 위치만 지운다. 일정, 알림, 앨범, 심박, 센서 같은 개인 자료는 계속 남는다.

2026-11-23 사용자가 탈퇴자가 올린 가족 앨범(사진, 영상, 그 앨범의 댓글, 좋아요, 조회, 잠금 해제)을 모두 지우기로 정했다(2-4, ADR-0043 후속 8). 같은 날 기획자 답변(사용자 전달)으로 아래가 확정됐다.

1. 다른 가족 앨범에 단 탈퇴자 댓글은 내용만 가리고 답글은 남긴다(5.3).
2. 탈퇴자 앨범의 잠금 해제에 쓴 포인트는 돌려주지 않는다.
3. 30일 안 복구는 없다. 30일은 삭제를 미루는 기간일 뿐이다.
4. 탈퇴 뒤 남기는 기록은 다섯 가지뿐이다: 결제 5년, 동의 1년, 관리자 접속기록 2년, 위치 열람 기록 1년(보호자가 탈퇴해도 그 보호자가 열람자인 행은 남긴다), 제품 개선 사본 3년(동의 철회 시 즉시 삭제). **실증 참여 기록도 남기지 않는다.** 기간이 지난 뒤 지우는 것은 보관 기간 정리 작업(LLD-0136, 보류) 몫이다.
5. 실증 참가자도 연구 자료, 센서 원문, 참여 기록과 회차까지 지운다.
6. 세기만 하는 dry-run 없이 바로 지운다(사용자 결정). 긴급 정지 스위치(`enabled`)만 둔다.

제품 개선 사본은 서버에 해당 자료가 없다(2026-11-23 `backend` 전체에서 제품 개선, 사본, `product improvement`, `deidentif`를 찾았으나 테이블, 엔티티, 설정이 없음).

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`Member.personalDataPurgedAt`, `DataPurgeLog.deleted`), widyu-api(정리 작업 서비스, 스케줄러, 대상 정의, JDBC 저장소, `S3ObjectDeletionTaskService.scheduleKeys`, 설정, Flyway V7)
- 탈퇴 시각이 30일 지난 회원의 개인 자료 표를 지운다. 회원 행은 묘비로 남긴다.
- 탈퇴자가 올린 앨범과 딸린 행을 지우고 S3 파일은 삭제 작업 큐로 지운다. 센서 원문과 실증 회차 내보내기 zip의 S3 객체도 같은 큐로 지운다.
- 실증 참여 기록과 그 회원의 회차(배정, 마커, 내보내기)를 지운다.
- `enabled` 설정(긴급 정지). 실행마다 단계별로 지운 건수를 `data_purge_log`에 남긴다.

### Out of scope
- 보관 기간 자동 삭제(LLD-0136 2, 3단계). 사용자가 결정 뒤로 미뤘다.
- 동의 기록의 「탈퇴 뒤 1년」 삭제(LLD-0136 9절 4번). 동의 기록은 남긴다.
- 결제(5년), 관리자 기록(2년)의 상한 삭제(ADR-0043 후속 9).
- 30일 안 복구(후속 6). 기획자 답변으로 복구는 없다. 탈퇴 즉시 이름과 번호를 가린다.
- 탈퇴자 앨범 잠금 해제 포인트 환불. 돌려주지 않는다(기획자 답변).
- 다른 가족 알림센터에 남은 탈퇴 어르신 이름(`fcm_notification.senior_display_name`). 그 행의 보관 기간까지 남는다(ADR-0043 결과).

## 3. 인터페이스 / API

새 API는 없다. 설정만 더한다(`application.yml`).

| 설정 | 기본값 | 뜻 |
| --- | --- | --- |
| `retention.withdrawal-purge.enabled` (`RETENTION_WITHDRAWAL_PURGE_ENABLED`) | `true` | false면 스케줄러 빈을 만들지 않는다 |
| `retention.withdrawal-purge.cron` (`RETENTION_WITHDRAWAL_PURGE_CRON`) | `0 50 3 * * *` | 매일 03:50 KST, 보관 기간 정리(03:30) 뒤 |

## 4. 데이터 모델

### 4.1 지우는 표와 순서 (`WithdrawnMemberPurgeStep`)

FK를 가리키는 쪽부터 지운다. 조건은 이 enum에만 있다.

| 묶음 | 단계(표) | 조건 |
| --- | --- | --- |
| 탈퇴자 앨범 | `album_comment`(답글 먼저, 다음 원댓글), `album_like`, `album_view`, `album_unlock`, `album_duration`, `album_media_url`, `album_thumbnail_url`, `album` | 탈퇴자가 올린 앨범에 딸린 행 전부 |
| 복약 | `medication_proof_images`, `medication_proof`, `medicine_schedule_detail`, `medicine_category`, `medicine_schedule` | 회원의 일정과 인증 |
| 일상 | `health_schedule`, `walk`, `address_bookmark`, `parent_location`, `senior_stay_state`, `member_notification_setting` | `member_id` |
| 알림 | `fcm_outbox`, `fcm_notification`, `member_fcm_token` | 받는 사람이 회원이거나 회원의 기기 토큰을 가리키는 행 |
| 계정 | `social_refresh_token`, `social_account`, `local_account`, `family_membership` | 회원의 계정과 가족 연결 |
| 위치 열람 | `location_access_log` | `senior_member_id`. 탈퇴 보호자가 본 행(`viewer_member_id`)은 어르신의 열람 확인 자료라 남긴다 |
| 어르신 프로필 | `point_history`, `senior_profile` | 주소, 생년월일, 포인트 |
| 연구 자료 | `followup_answer`, `followup_card`, `label_annotation`, `incident_label`, `incident_response_attempt`, `incident_guardian_response`, `incident`, `decision_record`, `heart_rate_event`, `heart_rate_emergency`, `device_heartbeat`, `device_event`, `activity_window`, `sensor_dropped_range`, `sensor_batch_rejection`, `location_fix`, `sensor_batch` | 회원 자료. 실증 참가자도 지운다 |
| 실증 회차와 참여 | `run_export`, `run_marker`, `run_device_assignment`, `collection_run`, `study_participation_consent`, `study_participation_history`, `study_participation_withdrawal_item`, `study_participation` | 4.3 |
| 처리 기록 | `social_unlink_task` | 끝난 소셜 연동 해제 작업 |

남기는 것: `member`(묘비), `consent_record`, `payment`와 딸린 표, `payment_order`, `admin_access_log`, 보호자가 열람자인 `location_access_log` 행(어르신이 탈퇴하면 그 어르신의 행은 LLD-0186이 탈퇴 때 이미 지웠다). 그 밖에 개인 내용이 없어 남기는 것:

- `admin_audit_log`: 관리자의 업무 행위 기록이다. 대상 회원은 ID로만 남는다. 관리자 접속기록과 같은 성격으로 본다.
- `s3_object_deletion_task`, `medication_proof_image_deletion_task`: 이 작업이 방금 저장한 S3 삭제 작업이 들어 있다. 지우면 파일이 지워지지 않는다.
- `clock_mapping`: 기기 단위 시계 매핑이라 다른 회원의 회차와 함께 쓴다. 회원 식별자가 없다.
- 탈퇴자가 다른 사람 앨범에 남긴 좋아요, 조회, 잠금 해제 행: 개인 내용이 없고 앨범 카운터와 맞물려 있다.
- 다른 가족 알림센터의 `fcm_notification.senior_display_name`: 받은 보호자의 자료라 그 행의 보관 기간까지 남는다.
- 다른 어르신 사건에 탈퇴 보호자가 남긴 대응(`incident_guardian_response.guardian_member_id`, `incident.guardian_response_by`, `resolved_by`): 그 어르신의 위급 대응 기록이자 실증 라벨이다. 남는 것은 개인정보 없는 묘비 회원의 ID뿐이고, 지우거나 비우면 다른 회원의 자료가 바뀐다. 그 사건의 보관 기간(180일, LLD-0136)이 지나면 함께 지워진다.

### 4.2 `member.personal_data_purged_at` (신규, `datetime(6)` NULL)

개인 자료를 지운 시각. 비어 있는 탈퇴 회원만 고른다. Flyway `V7__member_personal_data_purged_at.sql`(V6은 #1157의 탈퇴 요청 표 삭제).

### 4.3 실증 회차와 참여 기록의 삭제 순서

FK는 `run_device_assignment.run_id`, `run_marker.run_id` → `collection_run.id`, `collection_run.study_participation_id` → `study_participation`, `study_participation_consent`·`_history`·`_withdrawal_item` → `study_participation`이다. `run_export.run_id`는 FK 없이 `collection_run.run_id` 문자열을 가리킨다.

1. 그 회원 회차의 내보내기(`run_export`), 마커, 기기 배정
2. 그 회원의 회차(`collection_run.member_id`)
3. 참여 기록에 딸린 동의, 이력, 철회 항목
4. 참여 기록

회차는 `member_id`로만 고른다. 다른 회원의 회차가 이 회원의 참여 기록을 가리키면(회차 개설 검증 `RUN_RESEARCH_PARTICIPATION_MISMATCH`가 막으므로 정상 자료에는 없다) 4에서 FK 오류가 나 그 회원 처리가 롤백되고 실패로 기록된다. 다른 회원의 자료는 지우지 않는다. 기기 배정은 행만 지우고 기기 자체(`device_id`)와 시계 매핑은 건드리지 않는다.

### 4.4 `data_purge_log` 기록

실행마다 단계마다 한 행, 지운 건수를 `target_rows`에, `dry_run=false`, `exempt_rows`는 비운다. 대상은 `withdrawn.<단계 이름 소문자>`(예: `withdrawn.album`). 댓글 가림은 `withdrawn.album_comment_mask`, 실패한 회원이 있으면 `withdrawn.member` 행에 `error_type = FAILED_MEMBERS=<수>`를 남긴다. `cutoff_at`은 「이 시각보다 앞서 탈퇴한 회원」이다. 대상 회원이 없으면 기록하지 않는다. 회원 ID는 남기지 않는다(LLD-0136 4.3).

## 5. 처리 흐름

### 5.1 실행 (`WithdrawnMemberPurgeService.purge(now)`)

1. `withdrawn_at < now - 30일`이고 `personal_data_purged_at IS NULL`인 회원을 ID 순으로 최대 100명 고른다. 관리자 정지(`INACTIVE`, 탈퇴 시각 없음)는 고르지 않는다. 그 회원의 회차 내보내기가 대기 중(`QUEUED`)이거나 만드는 중(`RUNNING`)이면 다음 날로 미룬다. 아직 S3 키가 없어 지금 지우면 워커가 나중에 올린 zip이 남기 때문이다. 고른 뒤 정리 전에 내보내기가 새로 시작되는 틈은 막지 않는다(탈퇴 30일 뒤 내보내기는 드물다).
2. 회원마다 트랜잭션 하나로 5.2를 한다. 예외가 나면 그 회원만 롤백하고 WARN(회원 ID, 예외 클래스)을 남긴 뒤 다음 회원으로 간다. 다음 날 다시 고른다.
3. 성공한 회원의 건수를 단계별로 더해 4.4대로 남기고 INFO 요약을 남긴다.

### 5.2 회원 한 명

1. 지우기 전에 파일 목록을 읽어 S3 삭제 작업을 저장한다. 앨범 사진, 영상, 썸네일 주소와 남은 복약 인증 사진 주소는 `schedule`, 센서 원문 키와 회차 내보내기 zip 키는 `scheduleKeys`. 작업은 이 트랜잭션과 함께 커밋되고 커밋 뒤 지워지며 실패하면 기존 재시도 스케줄러가 맡는다(LLD-0030). 롤백되면 작업도 사라진다.
2. 4.1 순서로 단계마다 지운다.
3. 다른 가족 앨범에 단 댓글은 내용만 「탈퇴한 회원의 댓글입니다.」로 바꾼다(5.3).
4. `personal_data_purged_at`을 채운다.

### 5.3 다른 가족 앨범에 단 댓글

- **채택: 행을 남기고 내용만 가린다(2026-11-23 기획자 답변으로 확정).** 그 댓글에 다른 가족의 답글이 매달려 있다(`parent_comment_id` FK). 행을 지우면 다른 가족의 답글까지 지워야 하고, 앨범 댓글 수 카운터도 원자 UPDATE로 맞춰야 한다(LLD-0099).
- 기각: 탈퇴자 댓글과 그 답글을 함께 지운다. 다른 가족의 글이 사라진다.

### 5.4 센서 원문을 삭제 작업 큐로 지우는 한계

ADR-0043 대안 3은 전체 센서 정리(약 81만 행)에 큐를 기각했다. 여기서는 30일이 지난 탈퇴 회원 한 명의 묶음이라 건수가 작다. 인덱스 행을 지우기 전에 키를 큐에 넣으므로 객체 목록을 잃지 않는다. 탈퇴 회원이 한꺼번에 많아지면 보관 기간 정리 3단계의 `DeleteObjects` 경로로 옮긴다.

## 6. 예외 / 에러 처리

- 회원 단위 실패 격리(5.1). 실패 기록 저장 자체가 실패하면 예외가 스케줄러 밖으로 나가 `SchedulerErrorHandler`가 남긴다.
- 로그에는 회원 ID, 건수, 예외 클래스 이름만 남긴다. 좌표, 심박 값, 댓글 내용, S3 키는 남기지 않는다(LLD-0029).
- 새 에러 코드 없음.

## 7. 인수조건 (Acceptance Criteria)

- [x] 모든 단계의 삭제 문장이 빈 테이블에서 0건으로 실행되고 기록 대상 이름이 40자 안이다.
- [x] 30일이 지난 탈퇴 회원의 앨범, 그 앨범의 댓글과 답글, 좋아요, 심박, 위치 열람 기록이 지워지고 `personal_data_purged_at`이 채워진다. 단계별 지운 건수가 `dry_run=false`로 남고 답글, 원댓글, 가림 건수가 한 번씩만 세어진다.
- [x] 다른 가족 앨범에 단 탈퇴자 댓글은 내용만 가려지고 그 앨범과 답글은 남는다.
- [x] 30일이 지나지 않은 탈퇴 회원의 자료와 다른 회원의 앨범은 남는다.
- [x] 앨범 사진 주소로 S3 삭제 작업을 저장한다.
- [x] 지운 회원은 다음 실행에서 다시 고르지 않는다.
- [x] 회차 내보내기를 만드는 중인 탈퇴 회원은 지우지 않고 다음 실행으로 미룬다.
- [x] 실증 참가자도 심박, 참여 기록과 이력, 회차와 기기 배정, 마커, 내보내기가 지워지고 내보내기 zip의 S3 삭제 작업이 저장된다. 기기 시계 매핑은 남는다.
- [x] 실제 MySQL 8(일회용 컨테이너, Flyway V1~V7로 만든 스키마, `ddl-auto=validate`)에서 `WithdrawnMemberPurgeServiceTest` 5건과 `SeniorWithdrawalLocationDeletionTest` 2건을 돌려 자료가 있는 상태의 FK 삭제 순서(앨범 답글, 원댓글, 실증 참여 이력, 회차, 배정, 마커, 내보내기)와 다른 가족 앨범 댓글 가림, 내보내기 중인 회원 미루기를 확인했다. `FlywayMigrationMySqlTest`도 통과했다(2026-11-23).

## 8. 영향 범위 / 마이그레이션

- **Flyway V7**: `member.personal_data_purged_at` 추가. 개발 서버 배포 때 적용된다.
- **배포하면 바로 지운다.** 되돌릴 수 없다. 멈출 때는 `RETENTION_WITHDRAWAL_PURGE_ENABLED=false`로 재기동한다. 지운 건수는 `data_purge_log`의 `withdrawn.*` 행으로 본다.
- **첫 실행**: 개발 서버의 기존 탈퇴자 중 30일이 지난 회원이 한꺼번에 대상이 된다. 하룻밤 100명씩 나눠 처리한다.
- `backend/CLAUDE.md` retention 절, ERD를 갱신한다.

## 9. 미결정 사항 (Open Questions)

- [x] 1. 실증 참가자의 탈퇴 뒤 연구 자료(ADR-0043 후속 2). 2026-11-23 기획자 답변: 참여 기록까지 모두 지운다.
- [x] 2. 다른 가족 앨범에 단 댓글(5.3). 2026-11-23 기획자 답변: 내용만 가린다.
- [ ] 3. RDS 백업에 지운 자료가 남는 기간과 처리방침 문구(ADR-0043 후속 5).

## 10. 참고

- `WithdrawnMemberPurgeService`, `WithdrawnMemberPurgeStep`, `WithdrawnMemberPurgeRepository`, `WithdrawnMemberPurgeScheduler`
- 테스트: `WithdrawnMemberPurgeServiceTest`(H2 `@DataJpaTest`)
