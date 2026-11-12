# LLD-0136: 보관 기간이 지난 자료를 매일 밤 지우는 작업

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.
> LLD 하나 = PR 하나가 원칙. "하나의 PR에 넣기엔 diff가 너무 많다(파일 15개 이상)"면 LLD를 분리한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Draft (1단계 구현: #1015) |
| Issue | #1015 (1단계: 건수만 세기). 노션 「보관 기간이 지난 자료 지우기와 탈퇴 처리를 처리방침대로 맞추기」 이슈 1의 1-1, 1-2, 1-3 |
| 관련 ADR | ADR-0043 (ADR-0036 결정 6을 대체), ADR-0030, ADR-0034 |
| 작성자 | 김동균 (Claude Code) |
| 작성일 | 2026-11-10 (초안), 2026-11-12 (1단계 구현 반영) |

## 1. 목적 / 배경

처리방침은 심박과 센서 원본, 위치 좌표, 발송 대기 기록을 30일, 기기 상태와 일반 알림을 90일, 건강 변화 알림 관련 기록을 180일, 위치 열람 기록을 1년 보관한다고 약속한다. 서버는 기간이 지나도 아무것도 지우지 않는다. 알림센터 항목은 `expires_at`이 지나면 조회에서만 빠지고 행은 남는다. 이 LLD는 기간이 지난 행과 S3 원문을 매일 밤 지우고, 날짜와 테이블별 건수를 DB에 남기는 작업을 설계한다. 탈퇴와 관련된 삭제는 LLD-0137이 다룬다.

삭제는 되돌릴 수 없다. 그래서 세 단계로 나눠 배포한다.

| 단계 | PR | 하는 일 |
| --- | --- | --- |
| 1 | #1015 (이 PR) | 매일 밤 테이블별로 **지울 대상 건수만 센다**. 삭제 코드가 없다. 운영자가 건수와 참가자 제외 건수를 보고 대상 조건을 확인한다 |
| 2 | 다음 PR | DB 행 삭제. `dry-run` 스위치(기본 `true`)와 1,000건 묶음 삭제, `incident` 딸린 표 삭제, 하룻밤 상한 |
| 3 | 그다음 PR | `sensor_batch`의 S3 원문을 `DeleteObjects`로 먼저 지우고 성공한 행만 DB에서 지운다 |

대상을 고르는 조건(테이블별 기준 시각 칸, 보관 기간, 참가자 제외)은 1단계에서 `RetentionTarget` 한 곳에 만들고 2, 3단계가 그대로 쓴다. 건수를 셀 때와 지울 때 조건이 갈라지면 운영자가 확인한 건수가 실제 삭제와 달라진다.

## 2. 범위

### In scope (1단계, #1015)
- 변경 모듈: widyu-domain(작업 기록 엔티티 `DataPurgeLog`), widyu-api(대상 정의 `RetentionTarget`, 건수 조회 `RetentionPurgeRepository`, 서비스, 스케줄러, 설정)
- 4.1 표의 테이블마다 지울 대상 건수와 실증 참가자라 빼 둔 건수를 센다.
- 실행마다 테이블별 한 행을 `data_purge_log`에 남기고 같은 요약을 INFO 로그로 남긴다(1-2).
- `retention.purge.enabled`(기본 켜짐)와 `retention.purge.cron`(기본 매일 03:30) 설정.
- `data_purge_log` 생성 SQL. 운영 서버가 아직 없으므로 **운영 구축 때 적용**한다(8.1절).

### In scope (2, 3단계, 다음 PR)
- `retention.purge.dry-run` 스위치(기본 `true`)와 실제 삭제 경로(5.3절).
- `incident` 딸린 표 6개를 사건과 함께 지우기(5.4절). 1단계는 `incident`만 센다.
- `sensor_batch`와 S3 원문(5.5절).
- AWS 콘솔에서 사용자가 확인할 항목(1-3, 8.3절). 콘솔 확인 자체는 사용자가 한다.

### Out of scope
- 탈퇴 회원의 자료 삭제, 탈퇴 뒤 1년이 지난 동의 기록 삭제(LLD-0137). `consent_record` 규칙은 `member` 탈퇴 시각 칸이 생긴 뒤 4.1 표에 한 줄로 더한다(9절 4번).
- 관리자 접속기록, 관리자 업무 기록, 결제 기록의 상한 삭제(ADR-0043 결정 1).
- 실증 참가자의 연구 보관 기간(`research_until`) 만료 삭제. 지금처럼 수동 절차가 맡는다.
- `device_event`(LLD-0129). 처리방침 보관 표에 없다. 9절 7번.
- 위치 열람 기록 CSV 내보내기(1-4, LLD-0132), 운영 테이블 확인(1-5), DB SSL(이슈 3).
- 삭제된 앨범(`status = DELETED`)의 S3 파일 삭제. 보관 기간 표에 없다. 후속 이슈 후보다.
- 정리 작업 결과를 보는 관리자 화면. DB 표를 직접 조회한다.
- 여러 서버에서의 중복 실행 방지. 배포는 단일 인스턴스이고(ADR-0023), 기존 스케줄러도 ShedLock 같은 분산 잠금 없이 돈다(`SocialUnlinkRetryScheduler`, `PaymentRecoveryScheduler`의 `ponytail:` 주석). 1단계는 건수만 세므로 두 번 돌아도 `data_purge_log`에 행이 두 벌 남을 뿐이다. 2단계 삭제도 같은 조건의 `DELETE`라 두 번 돌면 두 번째가 0건을 지운다. 여러 인스턴스로 늘릴 때 다른 스케줄러와 함께 분산 잠금을 넣는다.

## 3. 인터페이스 / API

공개 API를 추가하거나 바꾸지 않는다. `application.yml`에 설정만 추가한다.

| 키 | 기본값 | 단계 | 뜻 |
| --- | --- | --- | --- |
| `retention.purge.enabled` | `true` (`RETENTION_PURGE_ENABLED`) | 1 | `false`면 스케줄러 빈을 만들지 않는다. 건수 조회가 DB에 부담이 되면 끈다 |
| `retention.purge.cron` | `0 30 3 * * *` (`RETENTION_PURGE_CRON`), `Asia/Seoul` | 1 | 매일 03:30 |
| `retention.purge.dry-run` | `true` | 2 | `true`면 지우지 않고 지울 건수만 기록한다 |
| `retention.purge.chunk-size` | `1000` | 2 | 한 번에 고르고 지우는 행 수. S3 `DeleteObjects` 한 번의 상한도 1,000이다 |
| `retention.purge.max-rows-per-table` | `300000` | 2 | 하룻밤 테이블당 상한. 넘으면 다음 날 이어서 지운다 |

1단계에 `dry-run` 스위치를 두지 않은 이유: 이 PR에는 삭제 코드가 없어 스위치가 막을 대상이 없다. 스위치를 두고 아무것도 하지 않으면 `false`로 바꾼 운영자가 삭제가 된다고 오해한다. 삭제 경로와 스위치는 2단계에서 함께 들어오고, 그때 기본값은 `true`다.

보관 기간은 설정으로 두지 않는다. 처리방침이 바뀌어야 바뀌는 값이라 코드의 enum 상수로 둔다.

## 4. 데이터 모델

### 4.1 대상 테이블과 조건 (`RetentionTarget`)

「기준 시각」은 서버가 행을 만든 시각이다(ADR-0043 결정 3). 컷오프는 `실행 시각 - 보관 기간`이고, 기준 시각이 컷오프보다 **앞선** 행만 대상이다. 정확히 컷오프 시각인 행은 남는다.

| 순서 | 테이블 (PK) | 보관 | 기준 시각 칸 | 추가 조건 (1단계) | 참가자 제외 칸 | 처리방침 근거 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `fcm_outbox` (`id`) | 30일 | `created_at` | `state IN ('SENT','CANCELLED','EXHAUSTED','EXPIRED')` | 없음 | 발송 대기 30일 |
| 2 | `fcm_notification` (`id`) | 종류별 90일 또는 180일 | `created_at` (90일로 범위만 좁힘) | `expires_at < 실행 시각` | 없음 | 알림센터 일반 90일, 건강 변화 180일, 안심구역 90일 |
| 3 | `heart_rate_event` (`heart_rate_event_id`) | 30일 | `created_at` | 없음 | `member_id` | 심박 측정값 30일 |
| 4 | `sensor_batch` (`sensor_batch_id`) | 30일 | `created_at` | 없음 | `member_id` | 심박 묶음과 움직임 센서 원본 30일 |
| 5 | `sensor_dropped_range` (`sensor_dropped_range_id`) | 30일 | `created_at` | 없음 | `member_id` | 움직임 센서 원본 30일 |
| 6 | `location_fix` (`location_fix_id`) | 30일 | `created_at` | `reason <> 'incident'` | `member_id` | 위치 좌표 30일 (4.2절) |
| 7 | `device_heartbeat` (`device_heartbeat_id`) | 90일 | `created_at` | 없음 | `member_id` | 기기 연결 상태 90일 |
| 8 | `heart_rate_emergency` (`heart_rate_emergency_id`) | 180일 | `created_at` | 없음 | `member_id` | 건강 변화 알림 기록 180일 |
| 9 | `decision_record` (`decision_record_id`) | 180일 | `created_at` | 없음 | `member_id` | 건강 변화 알림 판정 기록 180일 |
| 10 | `incident` (`incident_id`) | 180일 | `created_at` | 없음 | `member_id` | 알림 응답, 후속 질문 답 180일. 딸린 표는 2단계(5.4절) |
| 11 | `location_access_log` (`location_access_log_id`) | 1년 | `accessed_at` | 없음 | 없음 | 위치 열람 기록 1년 |

- `fcm_notification`의 `expires_at`은 생성 때 `FcmOutboxService.centerExpiresAt`이 `NotificationType.retentionClass()`(일반 90일, 건강 변화 180일, 안심구역 90일)로 채운다. 그래서 종류별 기간을 다시 계산하지 않고 이 칸을 읽는다. `created_at < 실행 시각 - 90일`은 가장 짧은 기간으로 훑을 PK 범위만 좁힌다.
- `fcm_notification`의 `expires_at`이 비어 있는 옛 행(종류가 없던 시절)은 **세지 않는다**(9절 3번).
- `fcm_outbox`의 끝나지 않은 상태(`PENDING`, `CLAIMED`)는 30일이 지나도 세지 않는다. 발송 중인 행을 지우면 발송기가 사라진 행을 처리하게 된다.
- `consent_record`는 **세지 않는다**. 「탈퇴 뒤 1년」의 기준인 탈퇴 시각 칸이 아직 없다(LLD-0137이 만든다, 9절 4번).

실증 참가자 제외 조건은 실행 때마다 DB에서 읽는다. 회원 ID 목록을 서버 메모리나 설정에 들고 있지 않는다.

```sql
<회원 칸> NOT IN (SELECT member_id FROM study_participation WHERE deletion_processed_at IS NULL)
```

`ACTIVE`, `ENDED`, 철회했지만 수동 삭제를 마치지 않은 `WITHDRAWN`이 모두 여기에 든다. 수동 삭제가 끝나 `deletion_processed_at`이 찍히면 그 회원은 다음 실행부터 대상이 된다. `member_id`는 `NOT NULL`이라 `NOT IN`이 NULL 때문에 0건이 되는 일이 없다. 알림센터, 발송 대기, 위치 열람 기록은 연구 자료가 아니므로 참가자도 대상이다(ADR-0043 결정 4).

### 4.2 「알림에 붙은 좌표」

처리방침은 좌표를 30일 보관하되 건강 변화 알림에 붙은 좌표는 180일, 안심구역 알림에 붙은 좌표는 90일 보관한다. 서버에서 확인한 사실은 다음과 같다.

- `location_fix`에는 어느 알림에 쓰였는지 가리키는 칸이 없다. 앱이 보내는 `reason`(`move`, `keepalive`, `incident`)만 있다.
- `heart_rate_emergency.location`은 단건 심박 경로가 요청에 실어 보낸 문자열을 그대로 저장한다. 묶음 경로는 `null`이다. 이 행은 180일 규칙(8번)을 따르므로 붙은 좌표도 180일 남는다.
- 안심구역 이탈과 복귀 알림은 좌표를 DB에 저장하지 않는다. 판정에 쓴 Redis 위치는 TTL로 사라진다.

식별 방법이 정해지지 않았으므로 1단계는 **`reason = 'incident'`인 행을 아예 세지 않는다**. 30일 대상에도, 180일 대상에도 넣지 않는다. 일반 위치를 덜 세는 쪽으로만 틀린다. 결정이 나면 `RetentionTarget.LOCATION_FIX`의 추가 조건 한 줄을 바꾸거나 `incident` 행을 180일로 세는 대상을 하나 더한다(9절 1번).

### 4.3 새 테이블 `data_purge_log` (widyu-domain `com.widyu.retention.DataPurgeLog`)

정리 작업 한 번, 테이블 하나당 한 행이다. 회원 ID, 좌표, 건강값은 넣지 않는다.

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `data_purge_log_id` | BIGINT PK | |
| `run_id` | VARCHAR(36) NOT NULL | 한 번의 실행을 묶는 UUID |
| `target` | VARCHAR(40) NOT NULL | 4.1 표의 테이블 이름 |
| `dry_run` | BIT NOT NULL | 지우지 않고 세기만 했는지. 1단계는 항상 `1` |
| `cutoff_at` | DATETIME(6) NOT NULL | 이 시각보다 앞선 행이 대상 |
| `target_rows` | BIGINT NULL | 조건에 맞는 행 수(참가자 제외 뒤). 2단계에서 실제로 지운 수는 칸을 따로 더한다. 실패하면 `NULL` |
| `exempt_rows` | BIGINT NULL | 기간이 지났지만 실증 참가자라 뺀 행 수. 참가자 제외가 없는 테이블과 실패한 행은 `NULL` |
| `error_type` | VARCHAR(100) NULL | 테이블 처리 중 예외 클래스 이름 |
| `started_at`, `finished_at` | DATETIME(6) NOT NULL | |

인덱스 `idx_data_purge_log_started (started_at)`. 생성 SQL은 `scripts/mysql/create_data_purge_log.sql`. 이 표 자체는 자동 삭제하지 않는다. 하루 11행이다.

2단계가 더할 칸(`deleted_rows`, `capped`), 3단계가 더할 칸(`deleted_objects`, `failed_objects`)은 1단계에 미리 만들지 않는다. 운영 서버가 아직 없어 그때 생성 SQL을 고치면 된다.

애플리케이션 로그(Loki 30일 보관)에도 같은 요약을 INFO로 남기지만, 1-2의 운영 기록은 이 DB 표다.

## 5. 처리 흐름

### 5.1 실행 (1단계)

1. `RetentionPurgeScheduler`(`@Scheduled(cron = "${retention.purge.cron}", zone = "Asia/Seoul")`, `retention.purge.enabled=true`일 때만 빈 등록)가 `RetentionPurgeService.countExpired(LocalDateTime.now())`를 부른다. 스케줄러는 `SchedulerConfig`의 전용 스케줄러에서 돌고 `widyu.scheduling.enabled=false`(테스트)면 실행되지 않는다. 메서드에 트랜잭션이 없다.
2. `run_id`를 만든다.
3. 4.1 표의 순서대로 테이블마다 5.2절로 건수를 센다. 한 테이블이 예외로 끝나도 `error_type`을 남기고 다음 테이블로 간다.
4. 테이블마다 `data_purge_log` 한 행을 저장한다(JPA `save`, 행마다 자기 트랜잭션).

### 5.2 건수 세기: 경계 PK를 찾고 PK 범위만 센다

대상 테이블 어디에도 `created_at`(또는 `accessed_at`) 인덱스가 없다. 있는 인덱스는 회원과 측정 시각 묶음(`idx_sensor_batch_member_stream_time` 등)이라 전체 기간 범위 조회에 쓸 수 없다. `WHERE created_at < :cutoff` 그대로 세면 테이블 전체를 훑는다. `sensor_batch`(약 81만 행)는 기간 안의 행까지 모두 읽는다.

대신 기준 시각이 PK와 함께 늘어난다는 사실을 쓴다(ADR-0043 결정 3). 행을 만들 때 서버 시각을 넣고 PK는 `AUTO_INCREMENT`다.

1. **경계 찾기** (`RetentionPurgeRepository.findBoundary`): `MIN(pk)`, `MAX(pk)`를 읽고 PK 값 범위를 이분 탐색한다. 탐색 한 번은 아래 한 문장이며 PK 인덱스로 한 행만 읽는다. 81만 행이면 약 20번이다.

   ```sql
   SELECT <기준 시각 칸> FROM <table> WHERE <pk> >= :pk ORDER BY <pk> LIMIT 1
   ```

   결과 `bound`는 「그 PK부터 처음 만나는 행이 컷오프 이후인」 가장 작은 PK 값이다. 표가 비었으면 0이다.
2. **세기** (`RetentionPurgeRepository.countTargets`, `countExempt`): `RetentionTarget.targetCondition()`과 `exemptCondition()`이 만든 조건으로 센다.

   ```sql
   SELECT COUNT(*) FROM <table>
    WHERE <pk> < :bound AND <기준 시각 칸> < :cutoff
      [AND (<추가 조건>)]
      [AND <회원 칸> NOT IN (SELECT member_id FROM study_participation WHERE deletion_processed_at IS NULL)]
   ```

   참가자 제외 건수는 마지막 줄만 `IN`으로 바꾼 같은 문장이다. 두 문장 모두 PK의 앞쪽 범위(`pk < :bound`), 곧 기간이 지난 행만 읽는다. 기간 안의 행은 읽지 않는다.

`pk < :bound`와 함께 `기준 시각 < :cutoff`도 거는 이유: 같은 밀리초에 동시에 들어온 행은 PK 순서와 시각 순서가 조금 어긋날 수 있다. 경계 근처에서 어긋난 행은 시각 조건이 빼서 **더 세지 않는다**. 경계 뒤에 숨은 기간 지난 행은 이번에 빠지고 다음 날 센다. 어느 쪽이든 「기간 안의 행을 대상으로 세는」 일은 없다.

조회는 잠금 없는 일관된 읽기(InnoDB consistent read)라 수집 경로의 `INSERT`를 막지 않는다. 대신 `COUNT`가 기간 지난 행 전체를 한 문장으로 읽는다. 1단계는 삭제하지 않으므로 날마다 이 범위가 하루치씩 늘어난다. 2단계가 지우기 시작하면 범위는 하루치로 줄어든다. 측정은 10절.

이 경계와 조건은 2단계 삭제가 그대로 쓴다. 바뀌는 것은 `COUNT(*)` 대신 `SELECT <pk> ... AND <pk> > :cursor ORDER BY <pk> LIMIT :chunk`로 1,000건씩 고르고 `DELETE ... WHERE <pk> IN (...)`으로 지우는 부분뿐이다.

테이블 이름과 칸 이름은 `RetentionTarget` enum 상수에서만 오고 요청 값이 SQL에 들어가지 않는다. 기존 의존성 `NamedParameterJdbcTemplate`(spring-boot-starter-data-jpa에 포함된 spring-jdbc)을 쓴다. 테이블마다 Spring Data 리포지토리 메서드를 만드는 것보다 짧고, 2단계의 묶음 삭제도 같은 방식으로 쓴다.

### 5.3 테이블 하나 지우기 (2단계, 다음 PR)

1. 5.2절의 경계와 조건으로 PK 오름차순 1,000건을 고른다. 기간 지난 행만 고르므로 수집 경로가 넣는 새 행과 같은 행을 잠그지 않는다.
2. `dry-run=false`면 고른 PK를 `DELETE ... WHERE <pk> IN (...)`으로 지운다. 묶음마다 `REQUIRES_NEW` 트랜잭션 하나다.
3. 커서를 묶음의 마지막 PK로 옮기고, 테이블당 상한에 닿으면 `capped=true`로 끝낸다.
4. `fcm_notification`을 지우기 전에 그 행을 가리키는 `fcm_outbox`를 같은 트랜잭션에서 먼저 지운다(FK `fk_fcm_outbox_notification`). 30일이 지나지 않은 발송 대기 행이 90일 지난 알림을 가리킬 일은 사실상 없지만 FK 오류를 막는다.

### 5.4 `incident`와 딸린 표 (2단계)

딸린 표의 FK는 모두 `incident`를 가리킨다(`scripts/mysql/create_followup_card.sql`, `create_incident_label.sql`, `create_incident_response_attempt.sql`, `alter_incident_second_alert.sql`). 기간이 지난 사건 묶음의 `incident_id`와 `incident_ref`를 고른 뒤 한 트랜잭션에서 다음 순서로 지운다.

1. `followup_answer` — 그 사건들의 `followup_card.id`를 가리키는 행
2. `followup_card` — `incident_ref`
3. `label_annotation` — 그 사건들의 `incident_label.id`를 가리키는 행
4. `incident_label` — `incident_ref`
5. `incident_response_attempt` — `incident_id`
6. `incident_guardian_response` — `incident_id`
7. `incident` — `incident_id`

딸린 행은 자기 생성 시각과 상관없이 부모 사건과 함께 지운다. 1단계는 `incident` 행만 센다. 딸린 행 수는 사건 수에 따라오므로 운영 판단(지울지 말지)에 따로 필요하지 않다.

### 5.5 `sensor_batch`와 S3 원문 (3단계)

1. 5.3절 1과 같이 묶음을 고른다. `s3_key`도 함께 읽는다.
2. 고른 행의 키로 `S3Service.deleteFilesByKeys(List<String>)`를 부른다. 이 메서드를 새로 만든다. AWS SDK v2의 `DeleteObjects`(quiet 모드, 1,000키 이하)를 한 번 부르고 실패한 키 목록을 돌려준다. 이 호출은 DB 트랜잭션 밖이다.
3. 응답의 `Errors`에 없는 키의 행만 DB에서 지운다. 없는 키를 지우는 요청도 S3는 성공으로 답하므로, 이미 지워진 객체의 행도 이 단계에서 정리된다.
4. 실패한 키의 행은 남는다. 다음 날에도 기간이 지난 상태라 다시 고른다. 따로 재시도 큐를 두지 않는다.
5. `DeleteObjects` 호출 자체가 예외로 실패하면 그 묶음의 행을 하나도 지우지 않고 테이블 처리를 끝낸다.

이 순서는 수동 절차(`study-withdrawal-procedure.md` 4절)와 같다. 인덱스 행이 남은 객체를 찾는 유일한 기준이므로 S3를 먼저 지운다. 같은 `batch_id`의 경합에서 진 고아 객체(ADR-0030)는 인덱스 행이 없어 이 작업이 찾지 못한다(9절 6번).

버킷 버전 관리가 켜져 있으면 `DeleteObjects`는 삭제 마커만 남기고 이전 버전이 남는다. 이 경우 `sensor/` 접두사에 「이전 버전 1일 뒤 만료」와 「만료된 삭제 마커 정리」 수명주기 규칙을 함께 둔다(8.3절).

`heart_rate_event.batch_id`가 가리키는 배치가 먼저 지워져도 FK가 없어 문제가 없다. 둘 다 30일이다.

## 6. 예외 / 에러 처리

- 테이블 하나의 예외는 그 테이블만 멈춘다. `target_rows`, `exempt_rows`를 `NULL`로, `error_type`에 예외 클래스 이름을 남기고 WARN 로그 뒤 다음 테이블로 간다. 실행 전체를 실패로 만들지 않는다.
- 실패 기록 저장마저 실패하면(DB 장애) 예외가 스케줄러 밖으로 나가 기존 `SchedulerErrorHandler`가 한 줄로 남긴다. 다음 날 다시 돈다.
- 로그와 `data_purge_log`에는 테이블 이름, 컷오프, 건수, 예외 클래스 이름만 남긴다. 회원 ID, S3 키, 좌표, 심박 값은 남기지 않는다(LLD-0029, 정책 1.6.7). 예외 메시지는 SQL과 값이 섞일 수 있어 남기지 않는다.
- 새 에러 코드는 없다. 공개 API가 없다.

## 7. 인수조건 (Acceptance Criteria)

### 1단계 (#1015)

- [x] `retention.purge.enabled=false`면 스케줄러 빈이 등록되지 않는다.
- [x] 실행하면 4.1 표의 11개 테이블마다 `data_purge_log`에 `dry_run=true` 행이 하나씩 남고, 어느 대상 테이블의 행도 줄지 않는다.
- [x] 기준 시각이 컷오프보다 앞선 행만 세고, 정확히 컷오프 시각인 행과 그 뒤 행은 세지 않는다.
- [x] `deletion_processed_at`이 비어 있는 `study_participation`을 가진 회원의 연구 자료 행은 `target_rows`에서 빠지고 `exempt_rows`에 세어진다. `deletion_processed_at`이 찍힌 참가자의 행은 `target_rows`에 든다.
- [x] `location_fix`의 `reason = 'incident'` 행은 기간이 지나도 세지 않는다.
- [x] `fcm_notification`은 `expires_at`이 실행 시각보다 앞선 행만 센다. `expires_at`이 비어 있는 행은 세지 않는다.
- [x] `fcm_outbox`는 끝난 상태의 행만 센다.
- [x] `location_access_log`는 `accessed_at` 기준 1년으로 세고 참가자를 빼지 않는다(`exempt_rows`가 `NULL`).
- [x] 한 테이블에서 예외가 나도 다음 테이블을 센다. 실패한 테이블 행에 `error_type`이 남는다.
- [x] 경계 PK 탐색은 PK 한 행 조회의 반복이고, 건수 조회는 PK 범위(`pk < bound`)만 읽는다. MySQL 8 실행 계획을 10절에 남긴다.
- [x] 로그와 `data_purge_log`에 회원 ID, 좌표, 심박 값이 없다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

### 2단계 (DB 삭제, 다음 PR)

- [ ] `dry-run=true`면 어느 테이블의 행도 줄지 않는다. `false`면 1단계와 같은 조건의 행이 지워지고 기간 안의 행은 남는다.
- [ ] 같은 회원의 `fcm_notification`, `fcm_outbox`, `location_access_log`는 참가자여도 지워진다.
- [ ] `fcm_notification`을 가리키는 `fcm_outbox` 행이 있어도 FK 오류 없이 함께 지워진다.
- [ ] 기간이 지난 `incident`를 지우면 딸린 표 6개가 FK 오류 없이 함께 지워진다.
- [ ] 테이블당 상한에 닿으면 멈추고 `capped=true`를 남기며, 다음 실행이 이어서 지운다.

### 3단계 (S3, 다음 PR)

- [ ] `sensor_batch`는 S3 삭제가 성공한 키의 행만 지운다. 일부 키가 실패하면 그 행은 남고 `failed_objects`에 세어지며, 다음 실행에서 다시 시도된다.
- [ ] `DeleteObjects` 호출이 예외로 끝나면 그 묶음의 행이 하나도 지워지지 않는다.
- [ ] 실증 참가자의 `sensor_batch` 행과 S3 객체는 남는다. `dry-run`이면 S3 호출이 없다.

## 8. 영향 범위 / 마이그레이션

### 8.1 DDL

- 신규 테이블 `data_purge_log`: `scripts/mysql/create_data_purge_log.sql`.
  - 개발 서버(`ddl-auto: update`)는 배포 때 Hibernate가 만든다. 스크립트를 따로 돌리지 않아도 된다.
  - 운영 서버는 아직 없다. **운영 구축 때 적용**한다. 운영은 `ddl-auto: validate`라 이 표가 없으면 기동이 실패한다.
- 대상 테이블에는 칸과 인덱스를 더하지 않는다. 대형 테이블 DDL이 없다(5.2절).
- 2단계 전에 운영 FK가 스크립트와 같은지 `SHOW CREATE TABLE`로 확인한다. 특히 `fcm_outbox.notification_id` FK와 사건 딸린 표의 FK.

### 8.2 배포 순서

1. 1단계 배포. 개발 서버에서 매일 03:30에 건수가 쌓인다.
2. 운영자가 `data_purge_log`의 테이블별 `target_rows`와 `exempt_rows`를 본다. 현장 시험 계정처럼 남겨야 할 회원의 자료가 대상에 들어 있으면 먼저 `study_participation`에 등록하거나 2단계를 미룬다. 확인 결과를 노션에 남긴다.

   ```sql
   SELECT target, cutoff_at, target_rows, exempt_rows, error_type
     FROM data_purge_log
    WHERE run_id = (SELECT run_id FROM data_purge_log ORDER BY data_purge_log_id DESC LIMIT 1);
   ```
3. 2단계 배포(`dry-run=true` 기본). 하룻밤 돌려 1단계와 같은 건수인지 본다.
4. `dry-run=false`로 바꾼다. 삭제는 되돌릴 수 없다.

### 8.3 AWS 콘솔에서 사용자가 확인할 항목 (1-3)

노션에 값을 적는다. 이 문서 작성자는 콘솔을 보지 않았다.

| 위치 | 확인할 값 | 왜 필요한가 |
| --- | --- | --- |
| RDS > 데이터베이스 > 유지 관리 및 백업 | 자동 백업 보관 기간(일), 백업 기간 | 지운 자료가 백업에 남는 최장 기간. 처리방침 문구 |
| RDS > 스냅샷 > 수동 | 수동 스냅샷 목록과 생성일 | 수동 스냅샷은 지울 때까지 남는다 |
| RDS > 자동 백업 > 복제됨, AWS Backup 계획 | 다른 리전 복제, 백업 계획 유무 | 사본이 더 있는지 |
| S3 > 버킷 > 속성 | 버전 관리 상태(사용, 일시 중지, 사용 안 함), 객체 잠금 | 켜져 있으면 삭제가 삭제 마커만 남긴다(5.5절) |
| S3 > 버킷 > 관리 > 수명 주기 규칙 | 규칙 이름, 접두사, 만료 일수, 이전 버전 만료 | 이미 있는 규칙이 연구 자료를 지우지 않는지 |
| S3 > 버킷 > 관리 > 복제 규칙 | 복제 대상 버킷 | 사본이 더 있는지 |
| S3 > 버킷 > 접두사별 객체 수 | `sensor/`, `exports/`, `albums/staging/` | 첫 정리 규모. 콘솔의 Storage Lens나 인벤토리 |
| EC2 > 스냅샷, AMI | EBS 스냅샷과 생성일 | Redis 위치 캐시와 Loki 로그가 같은 디스크에 있다 |

### 8.4 그 밖의 영향

- `HeartRateCleanupScheduler`를 되살리지 않는다. 이 작업이 심박을 포함한다.
- `backend/CLAUDE.md`의 「`heart_rate_event`는 자동 삭제하지 않는다(#650)」, `location.access`와 `consent`의 「자동 삭제가 없다」 문장은 **2단계에서** 바꾼다. 1단계는 지우지 않으므로 그 문장이 아직 사실이다.

## 9. 미결정 사항 (Open Questions)

사용자와 팀이 정해야 한다. 1단계는 아래 항목을 모두 **세지 않거나 빼는** 쪽으로 처리했다. 건수만 세므로 자료 손실은 없다. 2단계 전에 정해야 한다.

- [ ] 1. 「건강 변화 알림에 붙은 좌표」를 `location_fix.reason = 'incident'`로 식별해도 되는가. 앱이 `incident`를 언제 보내는지 확인이 필요하다. 아니면 `location_fix`를 모두 30일로 하고 180일 좌표는 `heart_rate_emergency.location`뿐이라고 정리할 것인가. **1단계: `incident` 행을 세지 않는다.** 안심구역 알림에 좌표를 붙여 90일 남겨야 하는지도 같이 정한다(지금은 저장하지 않는다).
- [ ] 2. 실증 참가자를 회원 단위로 빼는 것에 동의하는가(ADR-0043 결정 4). 참가자의 연구와 무관한 일상 자료도 30일보다 오래 남는다. 센서 현장 시험에 쓴 데모 계정의 자료를 남겨야 한다면 그 계정이 `study_participation`에 등록돼 있는가. **1단계: 회원 단위로 빼고 `exempt_rows`로 따로 센다.**
- [ ] 3. `expires_at`이 비어 있는 옛 알림센터 행을 `created_at + 90일`로 지워도 되는가. **1단계: 세지 않는다.**
- [ ] 4. 동의 기록의 「탈퇴 뒤 1년」. 기준 시각 칸(LLD-0137의 탈퇴 시각)과 그 회원의 모든 동의 행을 지우는지. **1단계: 세지 않는다.**
- [ ] 5. 위치 원본을 30일에 지우면 위치 수집 사실의 확인자료가 사라진다. 위치정보법의 확인자료 6개월 이상 보존이 수집 사실에도 적용되면, 좌표 없이 회원과 날짜별 수집 건수만 남기는 표가 필요하다.
- [ ] 6. 인덱스 행이 없는 S3 고아 객체(`sensor/` 경합 패자, 실패한 업로드)를 어떻게 찾을 것인가. S3 인벤토리와 DB를 대조하는 일회성 점검을 할 것인가.
- [ ] 7. `device_event`(LLD-0129, 기기 상태가 바뀐 시각)는 처리방침 보관 표에 없다. 「기기 연결 상태 90일」에 넣을 것인가. **1단계: 세지 않는다.**
- [ ] 8. 첫 실행 상한. 30만 행으로 며칠에 나눠 지울 것인가, 운영자가 한 번만 상한을 올릴 것인가.
- [ ] 9. RDS 자동 백업 보관 기간과 수동 스냅샷 정책(ADR-0043 후속 5번).

## 10. 참고

### 10.1 실행 계획과 시간 (로컬 MySQL 8, 합성 자료)

2026-11-12, 로컬 Docker `mysql:8.0`(8.0.44, `innodb_buffer_pool_size=256M`). `scripts/mysql/create_sensor_batch.sql` 그대로 만든 표에 합성 행 81만 개(데이터 364MB)를 넣었다. `created_at`은 PK와 함께 7.68초씩 늘려 2026-09-01부터 2026-11-12까지 72일에 걸쳤다. 회원 20명 중 2명은 `deletion_processed_at`이 빈 참가자, 1명은 삭제 처리를 마친 참가자다. 운영 자료와 행 크기, 버퍼 풀 상태가 다르므로 절대 시간은 참고값이다. 반복 3회(경계 탐색은 1회)이고 같은 세션에서 연달아 돌려 버퍼 풀이 데워진 상태다.

**실행 계획**

| 문장 | `type` | `key` | 실제로 읽은 행 |
| --- | --- | --- | --- |
| 경계 탐색 한 번 (`WHERE pk >= ? ORDER BY pk LIMIT 1`) | `range` | `PRIMARY` | 1 |
| 이 LLD의 건수 조회 (`pk < bound AND created_at < cutoff AND member_id NOT IN (...)`) | `range` | `PRIMARY` | 474,140 (기간 지난 행만) |
| 비교: 기준 시각만 거는 조회 (`created_at < cutoff AND member_id NOT IN (...)`) | `ALL` | 없음 | 810,000 (표 전체) |

참가자 조건은 두 문장 모두 `study_participation`을 한 번 구체화한 뒤 반조인(`Nested loop antijoin`, `Not exists`)으로 붙는다.

**시간 (ms)**

| 상황 | 경계 탐색 | 이 LLD의 대상 건수 | 참가자 제외 건수 | 비교 조회 |
| --- | --- | --- | --- | --- |
| 1단계 지금: 72일 중 30일 경과(474,140행 경과) | 2.2 (20번) | 301, 327, 336 | 235, 244, 274 | 392, 393, 397 |
| 2단계 평상시: 하루치만 경과(11,249행 경과) | 5.7 (20번) | 17, 5 | 측정 안 함 | 601, 394 |

건수는 직접 센 정답(경과 474,140 = 대상 426,726 + 참가자 47,414)과 같았다.

해석:

- 1단계처럼 지우지 않는 동안은 기간 지난 행이 날마다 쌓이므로 이 방식도 그 범위를 다 읽는다. 표 전체를 훑는 비교 조회보다 읽는 행이 적어(474,140 대 810,000) 약 20% 빠를 뿐이다.
- 2단계가 지우기 시작하면 기간 지난 행은 하루치만 남는다. 이 방식은 그 하루치(약 1만 행)만 읽어 5~17ms이고, 비교 조회는 표가 얼마나 남았든 표 전체를 읽는다(394~601ms). 차이는 표가 커질수록 벌어진다.
- 새 인덱스(`created_at`)를 만들면 비교 조회도 범위 조회가 되지만, 81만 행 표에 온라인 DDL이 필요하고 `INSERT`마다 인덱스 갱신 비용이 붙는다. PK 범위로 같은 효과를 내므로 만들지 않는다.

측정 SQL은 저장소에 두지 않았다. 표 생성 스크립트와 위 조건으로 다시 만들 수 있다.

### 10.2 그 밖

- 확인 못 한 사실: 운영 DB의 테이블별 행 수와 가장 오래된 행의 날짜, 운영 FK가 스크립트와 같은지, S3 버전 관리와 수명주기 규칙, 앱이 `reason = 'incident'`를 보내는 조건, 현장 시험 계정의 참가자 등록 여부. 행 수는 `SELECT table_name, table_rows FROM information_schema.tables WHERE table_schema = DATABASE();`로 대략 볼 수 있다.
- 제거된 심박 정리: 커밋 `f2c95dd4`(#650), 닫힌 제외 스위치 #637, PR #638
- 수동 연구 자료 삭제: `docs/operations/study-withdrawal-procedure.md`
- 기존 S3 삭제 재시도 구조: LLD-0030
- AWS: [DeleteObjects](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObjects.html), [버전 관리 버킷의 수명주기](https://docs.aws.amazon.com/AmazonS3/latest/userguide/lifecycle-configuration-examples.html)
