# LLD-0154: Flyway 도입과 기준 스키마

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1055 |
| 관련 ADR | - |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

`member`, `album`, `payment` 같은 핵심 테이블의 CREATE 문이 저장소에 없다. 개발 서버 테이블은 Hibernate `ddl-auto: update`가 만들었고, 그 뒤 변경은 `scripts/mysql/*.sql` 54개를 손으로 적용했다. 어느 스크립트가 어느 환경에 적용됐는지 남는 곳이 없다. 운영(prod)은 `ddl-auto: validate`라 스키마를 만들지 않으므로, 첫 운영 DB를 재현할 방법이 없다.

운영 서버를 아직 만들지 않은 지금 기준 스키마를 고정하고, 이후 변경은 버전 파일로만 쌓는다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api, docs
- Flyway(`flyway-core`, `flyway-mysql`, Spring Boot 3.3.5 의존성 관리 버전)를 넣는다. 버전 파일 위치는 Spring 기본값 `classpath:db/migration`이다.
- `V1__baseline.sql`: 2026-11-14 개발 서버 DB의 `mysqldump --no-data` 결과(MySQL 8.0.46, develop `513f5396` 배포 상태, 테이블 72개)에, 같은 날 #1069 배포로 생긴 `senior_stay_state` 1개(develop `9fe0f3e1` 배포 뒤 다시 덤프)를 더해 73개로 만들고 `AUTO_INCREMENT=` 시작값과 mysqldump 세션 설정 주석을 지운 것이다. FULLTEXT 인덱스의 `/*!50100 WITH PARSER ngram */`는 같은 뜻의 일반 구문으로 바꿨다. 그 밖의 정의(컬럼, 인덱스, FK, CHECK)는 개발 서버와 같다.
- 프로필별 Flyway 설정과 개발 서버 `ddl-auto` 전환(4절).
- 빈 MySQL에 버전 파일을 모두 적용하고 Hibernate validate로 엔티티와 대조하는 테스트 `FlywayMigrationMySqlTest`(일회용 MySQL 환경변수가 있을 때만 실행).
- 규칙 문서: `backend/CLAUDE.md`, `backend/AGENTS.md`, `docs/production-deployment.md`.

### Out of scope
- native ENUM 23개를 VARCHAR로 바꾸는 일, enum CHECK 제약 정리: #1058(LLD-0155)에서 `V2`로 한다.
- 개발 서버에만 남은 잔여물 정리(5절 표의 2, 3번): 동작에 영향이 없어 V1에 그대로 둔다.
- 테스트를 H2에서 Testcontainers MySQL로 옮기는 일: 별도 판단(이슈의 정책 확인 항목).
- 기존 `scripts/mysql/*.sql`: 지우지 않는다. 도입 전 이력으로 남긴다.

## 3. 인터페이스 / API

API 변경 없음.

## 4. 데이터 모델

### 도구 선택
Flyway를 쓴다(사용자 승인). 버전 파일이 일반 SQL이라 기존 수기 스크립트와 같은 형태로 쓸 수 있고, Spring Boot가 자동 구성(`FlywayAutoConfiguration`)과 의존성 관리 버전을 제공한다. Liquibase는 롤백 정의와 DB 중립 변경 기록이 장점이지만, 이 프로젝트는 MySQL 하나만 쓰고 SQL 스크립트 관행이 이미 있어 얻는 것이 적다.

### 프로필별 설정

| 프로필 | Flyway | `baseline-on-migrate` | `ddl-auto` | 이유 |
| --- | --- | --- | --- | --- |
| prod | 켬 | `false`(명시) | `validate`(유지) | 빈 DB에 V1부터 적용한다. 이력 테이블 없이 테이블이 있는 DB는 기동을 멈춰 기준을 건너뛴 채 뜨지 않게 한다. |
| dev | 켬 | `true`, `baseline-version: 1` | `update` → `validate` | 기존 DB는 V1을 실행하지 않고 적용된 것으로 표시한다. 5절 검증에서 V1과 엔티티가 일치해 `validate`로 바꾼다. |
| local | 켬 | `true`, `baseline-version: 1` | `update`(유지) | 기존 로컬 DB는 dev와 같이 표시한다. 빈 로컬 DB는 V1부터 적용한다. 로컬 실험 편의를 위해 `update`는 그대로 둔다. |
| test(H2) | 끔 | - | `create`(유지) | 버전 파일이 MySQL 문법이라 H2에서 돌릴 수 없다. |

Spring Boot는 엔티티 매니저보다 Flyway를 먼저 실행하므로, 기동 순서는 "버전 파일 적용 → Hibernate validate"다.

### dev `ddl-auto`를 `validate`로 바꾼 근거
- 5절의 검증에서 V1(개발 서버 덤프)에 Hibernate validate가 통과했다. 컬럼 타입, 누락 테이블, 누락 컬럼 기준으로 개발 서버와 엔티티가 일치한다.
- `update`는 NOT NULL을 풀지 않고 ENUM 값을 늘리지 않으며, 새 enum 컬럼에 그 시점 값의 CHECK를 건다. 버전 파일로만 바꾸기로 한 뒤에도 `update`를 두면 dev 스키마가 다시 버전 파일과 어긋난다.
- 바꾼 뒤에는 엔티티만 바꾸고 버전 파일을 빠뜨린 PR이 dev 배포에서 기동 실패로 드러난다. 운영에서 처음 드러나는 것보다 낫다.

## 5. 처리 흐름

### 개발 서버 첫 배포(이 PR 머지 후)
1. 앱이 기동하면서 Flyway가 `flyway_schema_history`가 없고 테이블이 있는 것을 본다.
2. `baseline-on-migrate`로 이력 테이블을 만들고 버전 1(baseline) 행을 쓴다. V1은 실행하지 않는다.
3. 남은 버전 파일이 없으므로 적용할 것이 없다.
4. Hibernate validate가 엔티티와 테이블을 대조한다.

### 빈 DB(운영 최초, 새 로컬)
1. Flyway가 V1을 실행해 73개 테이블을 만들고 이력에 남긴다. 이어서 V2 이후를 순서대로 적용한다.
2. Hibernate validate가 대조한다.

### 엔티티와 V1 대조 결과(2026-11-14, 로컬 일회용 MySQL 8.0.44)
빈 DB 두 개를 만들어 하나에는 Flyway로 V1을, 다른 하나에는 Hibernate `create`로 엔티티 스키마를 만든 뒤 `information_schema`로 테이블, 컬럼(타입, NULL, DEFAULT), 인덱스, FK, CHECK를 비교했다. Hibernate validate는 1번만 검사하고 나머지는 보지 않는다.

| # | 대상 | 개발 서버(V1) | 엔티티 | 영향, 처리 |
| --- | --- | --- | --- | --- |
| 1 | `fcm_notification.fcm_category` | native ENUM에 `UNREAD`가 없다 | `FcmCategory`에 `UNREAD`가 있다 | 이 값으로 알림 행을 저장하면 `Data truncated`. validate는 ENUM 값 목록을 보지 않아 통과한다. #1058에서 VARCHAR로 바꿔 해소한다. |
| 2 | `member_fcm_token.device_info` | NULL 허용 `varchar(255)` 컬럼이 있다 | 필드 없음 | `drop_fcm_token_device_info.sql`이 개발 서버에 적용되지 않은 흔적. NULL 허용이라 INSERT에 영향 없다. V1에 그대로 둔다. |
| 3 | `medication_proof_image_deletion_task` | 테이블이 있다 | 엔티티 없음 | 쓰지 않는 옛 테이블. V1에 그대로 둔다. |
| 4 | `medicine.ft_medicine_item_name` FULLTEXT(ngram), `medication_proof.idx_medication_proof_member_verified` | 있다 | JPA로 표현하지 않음 | 스크립트로 만든 인덱스. V1에 들어 있어 빈 DB에서도 생긴다(기존에는 스크립트를 빠뜨리면 약 검색이 실패할 수 있었다). |
| 5 | `album_duration`, `album_media_url`, `album_thumbnail_url` PK | `(album_id, display_order)` | `(display_order, album_id)` | 열 순서만 다르다. 같은 제약이다. |
| 6 | VARCHAR enum 컬럼의 CHECK 제약 | 30개(`<table>_chk_N`) | Hibernate `create`는 38개를 건다 | `update`가 컬럼을 만들 때 그 시점 enum 값으로 건 것이다. 새 enum 값 INSERT를 막는다. 엔티티 쪽 8개(`fcm_notification.type`, `fcm_outbox.notification_type`, `incident`의 `kind`, `state`, `guardian_response_type`, `member_notification_setting.category` 등)는 사용자가 이미 dev에서 지운 것이다. #1058에서 정리한다. |

NULL 허용 여부, DEFAULT, FK는 어긋남이 없었다. 엔티티에 맞추려고 V2를 따로 만들 항목은 없다.

## 6. 예외 / 에러 처리

| 상황 | 동작 |
| --- | --- |
| 버전 파일 실행 실패 | Flyway가 예외를 던져 앱이 뜨지 않는다. MySQL DDL은 트랜잭션이 아니므로 실패 전까지 실행된 문장은 남는다. 이력에 실패 행이 남으면 원인을 고친 뒤 `flyway repair`가 필요하다. |
| 머지된 버전 파일 내용 변경 | 체크섬 불일치로 기동 실패. 버전 파일은 고치지 않고 새 버전을 더한다. |
| prod: 이력 테이블 없이 테이블이 있는 DB | `baseline-on-migrate: false`라 "non-empty schema without schema history table"로 기동 실패. |
| 엔티티만 바뀌고 버전 파일 없음 | dev, prod 모두 Hibernate validate가 기동을 멈춘다. |

## 7. 인수조건 (Acceptance Criteria)

- [x] 빈 MySQL에 버전 파일을 적용하면 Hibernate validate가 통과한다 (`FlywayMigrationMySqlTest`, 일회용 MySQL 8.0.44).
- [x] V1에 `AUTO_INCREMENT=` 시작값, DEFINER, mysqldump 세션 설정이 없다.
- [x] test 프로필(H2)은 Flyway를 끄고 기존 테스트 결과가 바뀌지 않는다.
- [x] dev는 기존 DB를 버전 1로 표시하고(`baseline-on-migrate`) `ddl-auto: validate`로 뜬다. prod는 `baseline-on-migrate: false`, `ddl-auto: validate`다.
- [x] 스키마 변경은 버전 파일로만 한다는 규칙이 `backend/CLAUDE.md`, `backend/AGENTS.md`, `docs/production-deployment.md`에 있다.
- [x] 엔티티와 V1의 어긋남 목록이 5절에 있다.
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- **개발 서버 DB 쓰기**: 머지 후 첫 배포에서 Flyway가 `flyway_schema_history` 테이블을 만들고 baseline 행 1개를 쓴다. 다른 테이블은 바꾸지 않는다. 앱 DB 계정에 CREATE 권한이 필요하다(지금도 `update`가 테이블을 만들고 있어 있다).
- **dev `validate` 전환**: 이 PR 뒤에 머지되는 PR이 엔티티에 테이블이나 컬럼을 더하면서 `scripts/mysql`만 두면 dev 기동이 멈춘다. 2026-11-14 열린 PR 가운데 #1069(`senior_stay_state` 새 테이블, `create_senior_stay_state.sql`)가 해당한다. 그 PR은 스크립트를 `V<다음 번호>__create_senior_stay_state.sql`로 옮겨야 한다. 반대로 #1069가 먼저 머지되면 dev에는 `update`가 테이블을 만들지만 V1에는 없어서, 빈 DB(운영)에서 validate가 실패한다. 이 경우에도 같은 V 파일이 필요하다(개발 서버에서는 `CREATE TABLE IF NOT EXISTS`라 무해).
- **병렬 PR의 버전 번호**: 두 PR이 같은 번호를 쓰면 나중 PR이 번호를 올려야 한다. 머지된 번호보다 낮은 새 파일은 Flyway가 기본 설정(`out-of-order: false`)에서 거부한다.
- **롤백**: 이전 이미지로 되돌리면 Flyway가 없는 코드가 `update`로 뜬다. 이력 테이블은 남아도 옛 코드가 읽지 않는다.

## 9. 미결정 사항 (Open Questions)

- [ ] 테스트를 H2에서 Testcontainers MySQL로 옮길지(이슈의 정책 확인 항목, 이번 범위 밖). 지금은 `FlywayMigrationMySqlTest`를 일회용 MySQL로 손으로 돌린다. CI에 MySQL 서비스를 붙여 이 테스트를 자동으로 돌릴지도 같이 정한다.
- [ ] 5절 표의 2, 3번(개발 서버에만 남은 컬럼과 테이블)을 지울지. 지금은 동작 영향이 없어 둔다.

## 10. 참고

- 2026-11-14 저장소 점검 DB 보고서 [높음-1]
- [Flyway baselineOnMigrate](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-baseline-on-migrate-setting)
- #672 (enum 컬럼 매핑 규칙)
