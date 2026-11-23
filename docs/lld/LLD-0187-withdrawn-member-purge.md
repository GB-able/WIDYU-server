# LLD-0187: 탈퇴한 지 30일이 지난 회원의 개인 자료와 올린 앨범을 매일 밤 지운다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1158 |
| 관련 ADR | ADR-0043 결정 4(실증 참가자 연구 자료 제외), 결정 7(탈퇴 30일 뒤 삭제와 묘비), 결정 9(dry-run 먼저) |
| 관련 LLD | LLD-0186(어르신 탈퇴 실행, 선행), LLD-0137(탈퇴 시각), LLD-0136(보관 기간 정리, `data_purge_log`), LLD-0030(S3 삭제 작업 큐) |
| 작성자 | Claude |
| 작성일 | 2026-11-23 |

## 1. 목적 / 배경

개인정보 처리방침은 「탈퇴하면 30일 뒤 지운다」고 약속한다(노션 「보관 기간이 지난 자료 지우기와 탈퇴 처리를 처리방침대로 맞추기」 2-2). 지금 탈퇴는 이름과 번호를 가리고 복약 인증 사진, 프로필 사진, Redis 실시간 위치만 지운다. 일정, 알림, 앨범, 심박, 센서 같은 개인 자료는 계속 남는다.

2026-11-23 사용자가 탈퇴자가 올린 가족 앨범(사진, 영상, 그 앨범의 댓글, 좋아요, 조회, 잠금 해제)을 모두 지우기로 정했다(2-4, ADR-0043 후속 8).

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`Member.personalDataPurgedAt`, `DataPurgeLog.recorded`), widyu-api(정리 작업 서비스, 스케줄러, 대상 정의, JDBC 저장소, `S3ObjectDeletionTaskService.scheduleKeys`, 설정, Flyway V6)
- 탈퇴 시각이 30일 지난 회원의 개인 자료 표를 지운다. 회원 행은 묘비로 남긴다.
- 탈퇴자가 올린 앨범과 딸린 행을 지우고 S3 파일은 삭제 작업 큐로 지운다. 센서 원문 S3 객체도 같은 큐로 지운다.
- `enabled`와 `dry-run`(기본 켜짐) 설정. 실행마다 단계별 건수를 `data_purge_log`에 남긴다.

### Out of scope
- 보관 기간 자동 삭제(LLD-0136 2, 3단계). 사용자가 결정 뒤로 미뤘다.
- 동의 기록의 「탈퇴 뒤 1년」 삭제(LLD-0136 9절 4번). 동의 기록은 남긴다.
- 결제(5년), 관리자 기록(2년)의 상한 삭제(ADR-0043 후속 9).
- 30일 안 복구(후속 6). 탈퇴 즉시 이름과 번호를 가리므로 복구하지 않는다.
- 다른 가족 알림센터에 남은 탈퇴 어르신 이름(`fcm_notification.senior_display_name`). 그 행의 보관 기간까지 남는다(ADR-0043 결과).

## 3. 인터페이스 / API

새 API는 없다. 설정만 더한다(`application.yml`).

| 설정 | 기본값 | 뜻 |
| --- | --- | --- |
| `retention.withdrawal-purge.enabled` (`RETENTION_WITHDRAWAL_PURGE_ENABLED`) | `true` | false면 스케줄러 빈을 만들지 않는다 |
| `retention.withdrawal-purge.dry-run` (`RETENTION_WITHDRAWAL_PURGE_DRY_RUN`) | `true` | true면 지우지 않고 건수만 센다 |
| `retention.withdrawal-purge.cron` (`RETENTION_WITHDRAWAL_PURGE_CRON`) | `0 50 3 * * *` | 매일 03:50 KST, 보관 기간 정리(03:30) 뒤 |

## 4. 데이터 모델

### 4.1 지우는 표와 순서 (`WithdrawnMemberPurgeStep`)

FK를 가리키는 쪽부터 지운다. 조건은 이 enum에만 있고 건수와 삭제가 같은 조건을 쓴다.

| 묶음 | 단계(표) | 조건 |
| --- | --- | --- |
| 탈퇴자 앨범 | `album_comment`(답글 먼저, 다음 원댓글), `album_like`, `album_view`, `album_unlock`, `album_duration`, `album_media_url`, `album_thumbnail_url`, `album` | 탈퇴자가 올린 앨범에 딸린 행 전부 |
| 복약 | `medication_proof_images`, `medication_proof`, `medicine_schedule_detail`, `medicine_category`, `medicine_schedule` | 회원의 일정과 인증 |
| 일상 | `health_schedule`, `walk`, `address_bookmark`, `parent_location`, `senior_stay_state`, `member_notification_setting` | `member_id` |
| 알림 | `fcm_outbox`, `fcm_notification`, `member_fcm_token` | 받는 사람이 회원이거나 회원의 기기 토큰을 가리키는 행 |
| 계정 | `social_refresh_token`, `social_account`, `local_account`, `family_membership` | 회원의 계정과 가족 연결 |
| 위치 열람 | `location_access_log` | `senior_member_id`. 탈퇴 보호자가 본 행(`viewer_member_id`)은 어르신의 열람 확인 자료라 남긴다 |
| 어르신 프로필 | `point_history`, `senior_profile` | 주소, 생년월일, 포인트 |
| 연구 자료 | `followup_answer`, `followup_card`, `label_annotation`, `incident_label`, `incident_response_attempt`, `incident_guardian_response`, `incident`, `decision_record`, `heart_rate_event`, `heart_rate_emergency`, `device_heartbeat`, `device_event`, `activity_window`, `sensor_dropped_range`, `sensor_batch_rejection`, `location_fix`, `sensor_batch` | 회원 자료. 실증 참가자면 지우지 않는다(4.3) |

남기는 표: `member`(묘비), `consent_record`, `payment`와 딸린 표, `payment_order`, `admin_access_log`, `admin_audit_log`, `member_withdrawal_request`, `study_participation`과 딸린 표, `collection_run`, `social_unlink_task`, `s3_object_deletion_task`, `medication_proof_image_deletion_task`. 탈퇴자가 다른 사람 앨범에 남긴 좋아요, 조회, 잠금 해제 행도 남긴다. 개인 내용이 없고 앨범 카운터와 맞물려 있다.

### 4.2 `member.personal_data_purged_at` (신규, `datetime(6)` NULL)

개인 자료를 지운 시각. 비어 있는 탈퇴 회원만 고른다. dry-run은 채우지 않는다. Flyway `V6__member_personal_data_purged_at.sql`.

### 4.3 실증 참가자

연구 자료 수동 삭제를 마치지 않은 참여(`study_participation.deletion_processed_at IS NULL`)가 있는 회원은 연구 자료 단계를 지우지 않고 그 건수를 `exempt_rows`에 센다(ADR-0043 결정 4). 연구 자료가 아닌 단계와 앨범은 참가자도 지운다. 센서 원문 S3도 남긴다.

### 4.4 `data_purge_log` 기록

실행마다 단계마다 한 행, 대상은 `withdrawn.<단계 이름 소문자>`(예: `withdrawn.album`). 댓글 가림은 `withdrawn.album_comment_mask`, 실패한 회원이 있으면 `withdrawn.member` 행에 `error_type = FAILED_MEMBERS=<수>`를 남긴다. `cutoff_at`은 「이 시각보다 앞서 탈퇴한 회원」이다. 대상 회원이 없으면 기록하지 않는다. 회원 ID는 남기지 않는다(LLD-0136 4.3).

## 5. 처리 흐름

### 5.1 실행 (`WithdrawnMemberPurgeService.purge(now, dryRun)`)

1. `withdrawn_at < now - 30일`이고 `personal_data_purged_at IS NULL`인 회원을 ID 순으로 최대 100명 고른다. 관리자 정지(`INACTIVE`, 탈퇴 시각 없음)는 고르지 않는다.
2. 회원마다 트랜잭션 하나로 5.2를 한다. 예외가 나면 그 회원만 롤백하고 WARN(회원 ID, 예외 클래스)을 남긴 뒤 다음 회원으로 간다. 다음 날 다시 고른다.
3. 성공한 회원의 건수를 단계별로 더해 4.4대로 남기고 INFO 요약을 남긴다.

### 5.2 회원 한 명

1. 실증 참가자인지 확인한다.
2. dry-run이 아니면 지우기 전에 파일 목록을 읽어 S3 삭제 작업을 저장한다. 앨범 사진, 영상, 썸네일 주소와 남은 복약 인증 사진 주소는 `schedule`, 참가자가 아니면 센서 원문 키는 `scheduleKeys`. 작업은 이 트랜잭션과 함께 커밋되고 커밋 뒤 지워지며 실패하면 기존 재시도 스케줄러가 맡는다(LLD-0030). 롤백되면 작업도 사라진다.
3. 4.1 순서로 단계마다 dry-run이면 세고, 아니면 지운다. 참가자의 연구 자료 단계는 세기만 하고 제외 건수로 남긴다.
4. 다른 가족 앨범에 단 댓글은 내용만 「탈퇴한 회원의 댓글입니다.」로 바꾼다(5.3).
5. dry-run이 아니면 `personal_data_purged_at`을 채운다.

### 5.3 다른 가족 앨범에 단 댓글

- **채택: 행을 남기고 내용만 가린다.** 그 댓글에 다른 가족의 답글이 매달려 있다(`parent_comment_id` FK). 행을 지우면 다른 가족의 답글까지 지워야 하고, 앨범 댓글 수 카운터도 원자 UPDATE로 맞춰야 한다(LLD-0099).
- 기각: 탈퇴자 댓글과 그 답글을 함께 지운다. 다른 가족의 글이 사라진다.

### 5.4 센서 원문을 삭제 작업 큐로 지우는 한계

ADR-0043 대안 3은 전체 센서 정리(약 81만 행)에 큐를 기각했다. 여기서는 30일이 지난 탈퇴 회원 한 명의 묶음이라 건수가 작다. 인덱스 행을 지우기 전에 키를 큐에 넣으므로 객체 목록을 잃지 않는다. 탈퇴 회원이 한꺼번에 많아지면 보관 기간 정리 3단계의 `DeleteObjects` 경로로 옮긴다.

## 6. 예외 / 에러 처리

- 회원 단위 실패 격리(5.1). 실패 기록 저장 자체가 실패하면 예외가 스케줄러 밖으로 나가 `SchedulerErrorHandler`가 남긴다.
- 로그에는 회원 ID, 건수, 예외 클래스 이름만 남긴다. 좌표, 심박 값, 댓글 내용, S3 키는 남기지 않는다(LLD-0029).
- 새 에러 코드 없음.

## 7. 인수조건 (Acceptance Criteria)

- [x] 모든 단계의 건수, 삭제 문장이 빈 테이블에서 0건으로 실행된다.
- [x] dry-run이면 어느 행도 줄지 않고 단계별 지울 건수가 `dry_run=true`로 남으며 S3 작업이 없다.
- [x] dry-run을 끄면 30일이 지난 탈퇴 회원의 앨범, 그 앨범의 댓글과 답글, 좋아요, 심박, 위치 열람 기록이 지워지고 `personal_data_purged_at`이 채워진다.
- [x] 다른 가족 앨범에 단 탈퇴자 댓글은 내용만 가려지고 그 앨범과 답글은 남는다.
- [x] 30일이 지나지 않은 탈퇴 회원의 자료와 다른 회원의 앨범은 남는다.
- [x] 앨범 사진 주소로 S3 삭제 작업을 저장한다.
- [x] 지운 회원은 다음 실행에서 다시 고르지 않는다.
- [x] 실증 참가자는 연구 자료가 남고 제외 건수로 세며 센서 원문 S3 작업이 없다. 앨범은 지운다.
- [x] MySQL 8(V1~V6 적용, 일회용 컨테이너)에서 47개 단계의 건수, 삭제 문장과 파일 목록, 가림, 완료 표시 문장이 문법 오류 없이 실행된다(빈 표). 자료를 넣은 FK 순서 확인은 H2 테스트로 했다. `FlywayMigrationMySqlTest`가 V6 적용 뒤 엔티티 검증을 통과한다.

## 8. 영향 범위 / 마이그레이션

- **Flyway V6**: `member.personal_data_purged_at` 추가. 개발 서버 배포 때 적용된다.
- **켜는 순서**: 배포하면 dry-run으로 매일 건수만 쌓인다. 운영자가 `data_purge_log`의 `withdrawn.*` 건수와 참가자 제외 건수를 확인한 뒤 `RETENTION_WITHDRAWAL_PURGE_DRY_RUN=false`로 바꾼다. 되돌릴 수 없는 삭제다.
- **첫 실행**: 개발 서버의 기존 탈퇴자 중 30일이 지난 회원이 한꺼번에 대상이 된다. 하룻밤 100명씩 나눠 처리한다.
- `backend/CLAUDE.md` retention 절, ERD를 갱신한다.

## 9. 미결정 사항 (Open Questions)

- [ ] 1. 실증 참가자가 탈퇴했을 때 연구 자료를 연구 보관 기간까지 남길지, 참여 철회로 보고 지울지(ADR-0043 후속 2). 지금은 남기고 제외 건수로 센다.
- [ ] 2. 다른 가족 앨범에 단 댓글을 가리는 방식(5.3)에 동의하는가.
- [ ] 3. RDS 백업에 지운 자료가 남는 기간과 처리방침 문구(ADR-0043 후속 5).

## 10. 참고

- `WithdrawnMemberPurgeService`, `WithdrawnMemberPurgeStep`, `WithdrawnMemberPurgeRepository`, `WithdrawnMemberPurgeScheduler`
- 테스트: `WithdrawnMemberPurgeServiceTest`(H2 `@DataJpaTest`)
