# LLD-0095: 시니어 푸시 설정 네 항목

> #881의 API, 발송 자격, 기존 데이터 이행을 검증하는 기준이다. #1105(2026-11-18)에서 네 항목을 켜짐 고정으로 바꿨다. 현재 동작은 §11이 정본이고, §3~§8의 「시니어가 끄면 막힌다」는 #881 당시 기록이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #881, #1105 |
| 관련 ADR | ADR-0037 결정 5 |
| 작성자 | Codex / feature/881, Claude / feature/1105 |
| 작성일 | 2026-10-26 (갱신 2026-11-18) |

## 1. 목적 / 배경

시니어 앱은 앨범·목표·홈·기타 푸시를 각각 켜고 끄는 화면을 제공한다. 서버는 현재 `GENERAL` 하나만 반환하고 나머지 변경을 거부한다. 시니어 설정을 화면과 맞추면서 보호자 네 그룹 및 필수 안전 알림 계약을 유지한다.

## 2. 범위

### In scope

- `widyu-domain`: 시니어용 `ALBUM`, `GOAL`, `HOME`, `ETC` 설정 그룹.
- `widyu-api`: 본인 설정 GET/PATCH, 수신자 타입별 발송 전 판정, Swagger, 테스트.
- `scripts/mysql`: 기존 시니어 `GENERAL` 값 이행 SQL. 운영에서 자동 실행하지 않는다.

### Out of scope

- 보호자 네 그룹, 다른 회원의 설정 조회·변경, OS 권한. 공통 문자는 FCM이 아닌 SMS 채널이므로 #881에서 전송 정책을 바꾸지 않는다.

## 3. 인터페이스 / API

`GET /api/v1/fcm/settings`는 시니어에게 `ALBUM`, `GOAL`, `HOME`, `ETC` 네 항목을 순서대로 반환한다. 기존 응답 필드와 `FCM_2010` 래퍼를 유지한다. `PATCH /api/v1/fcm/settings`는 `{"group":"ALBUM","enabled":false,"policyRevision":4}`처럼 본인 항목 하나를 바꾸고 `FCM_2011`로 반환한다. 시니어가 보호자 그룹 또는 `GENERAL`을 PATCH하면 `FCM_4001`이다. 보호자 계약은 LLD-0065와 같다.

## 4. 데이터 모델

`member_notification_setting.category`는 `VARCHAR(32)`이며 새 enum 값을 저장한다. 새 컬럼과 테이블은 없다. 회원×그룹 유일성과 기본 켜짐, `member.notification_policy_revision`을 유지한다. 기존 시니어 `GENERAL=false`는 네 그룹 모두 `false`로 옮겨 전체 푸시 거부를 보존한다. `GENERAL=true`는 기본 켜짐과 같아 새 행을 만들지 않는다. 보호자 `GENERAL`은 유지한다. 과거 개별 OFF 행은 LLD-0065 마이그레이션에서 삭제돼 복원할 수 없다.

## 5. 처리 흐름

1. GET은 인증 본인의 회원 타입으로 표시 그룹을 고른다. 행이 없으면 켜짐이며 모든 행이 같은 현재 revision을 갖는다.
2. PATCH는 회원 행을 잠그고 revision과 역할별 허용 그룹을 검사한다. 실제 값이 바뀔 때만 본인 설정을 저장하고 revision을 올린다.
3. typed outbox는 타입과 수신자 역할로 설정 그룹을 정한다. 보호자는 `NotificationType.settingGroup()`을 유지한다. 시니어는 `ALBUM→ALBUM`, `TARGET/HEALTH_SCHEDULE/WALK/MEDICINE_SCHEDULE→GOAL`, `HEART_MESSAGE→HOME`, `ETC→ETC`로 분기한다. 나머지 카테고리는 설정 그룹이 없고, `NONE`·`DATA_ONLY`는 설정을 건너뛴다. type 없는 레거시 outbox도 같은 역할별 카테고리 매핑을 쓴다. 긴급 본인확인 `SAFETY_SELF_CHECK`는 기존 안전 예외를 유지해 HOME OFF에도 발송한다.
4. claim과 preflight는 같은 자격 판정을 사용한다. OFF는 푸시만 취소하고, 센터 저장 타입의 행은 남긴다.

## 6. 예외 / 에러 처리

| 조건 | 결과 |
| --- | --- |
| 잘못된 그룹·시니어의 보호자 그룹·`GENERAL` 변경 | `FCM_4001` / 400, 상태 불변 |
| 낡은 revision | `FCM_4091` / 409, 상태 불변 |
| 방장 필수 안전 그룹 끄기 | 기존 `FCM_4090` / 409 유지 |
| OFF·토큰 없음 | 해당 푸시 전송 없음. 센터 행은 타입 정책대로 유지 |

## 7. 인수조건

- [x] AC1: 시니어 GET은 네 독립 값을, 보호자 GET은 기존 네 그룹을 반환한다.
- [x] AC2: 시니어 PATCH는 본인 한 값과 revision만 바꾸며 잘못된 그룹·낡은 revision을 거부한다.
- [x] AC3: 시니어 typed·legacy outbox는 해당 그룹을 claim·preflight에서 따르고, 보호자·필수 안전·data 전용 흐름은 유지한다.
- [x] AC4: 푸시 OFF에도 센터 저장 타입의 행은 남는다.
- [ ] AC5: 제안한 데이터 이행 SQL의 H2 매핑·재실행 테스트는 통과했다. 제품 확인과 운영 MySQL 검증은 남았다.
- [ ] AC6: Swagger와 변경 모듈 지정 테스트·정적 하네스는 통과했다. 전체 하네스는 Redis 가능한 환경에서 재실행해야 한다.

## 8. 영향 범위 / 마이그레이션

운영의 `member_notification_setting.category`는 선행 LLD-0065에 따라 `VARCHAR(32)`이어야 한다. `scripts/mysql/migrate_senior_notification_settings.sql`은 시니어 `GENERAL=false`만 네 OFF 행으로 복사한 뒤 시니어 `GENERAL`을 삭제한다. 새 그룹 행이 이미 있으면 사전 검사에서 중지한다. 구 앱 설정 쓰기 중지→백업·사전 검사→데이터 이행→새 서버·앱 계약 전환 순서다. 운영 SQL은 자동 실행하지 않는다. 이행 중 구 앱의 `GENERAL` 쓰기가 계속되면 상태가 갈라진다.

## 9. 미결정 사항 (Open Questions)

- 제품 확인 대기: HOME OFF에도 긴급 본인확인 푸시를 보내는 기존 안전 예외를 유지할지 확인한다. 현재 구현은 기존 예외를 보존한다.
- 제품 확인 대기: 공통 문자 스위치가 제어할 SMS 대상과 수신 설정의 저장·적용 범위를 정한다. #881의 FCM 구현에는 포함하지 않았다.
- 데이터 이행 제안: 기존 시니어 `GENERAL=false`를 네 OFF 항목으로 옮긴다. 과거 개별 OFF 상태 복원은 불가능하므로 배포 전 제품 확인이 필요하다.

## 10. 참고

- [기존 설정 설계](LLD-0065-notification-setting-groups.md)
- [알림센터 결정](../adr/ADR-0037-notification-center-model.md)
- [이슈 #881](https://github.com/GB-able/WIDYU-server/issues/881)

## 11. 갱신: 시니어 네 항목 켜짐 고정 (#1105, 2026-11-18)

### 11.1 배경

위듀(어르신) 알림 정책서 2절은 앱 안 알림 설정 화면을 없애고 어르신이 받는 알림을 끌 수 없게 정했다. #881 구현대로면 시니어가 `GOAL`을 끄면 복약 인증 재알림(M02, M03)과 일정 임박(H01)이, `ALBUM`을 끄면 새 게시물(A02-S)이, `HOME`을 끄면 응원 메시지가 막힌다. 이미 저장된 OFF 행도 같은 효과를 낸다.

### 11.2 변경 동작

- 발송 판정(`FcmEligibility`): 수신자가 시니어면 타입 유무와 관계없이 개인 설정을 보지 않는다. ACTIVE, 토큰 owner, 관련 회원 가족 검증은 그대로다. 보호자 판정(타입의 `settingGroup()`, 타입 없으면 `fromLegacy(FcmCategory)`, 방장 필수 안전 그룹)은 바뀌지 않는다. 시니어 전용 매핑(`PushSettingGroup.forSenior`, `fromLegacy(category, memberType)`)은 쓰는 곳이 없어 지웠다.
- `GET /api/v1/fcm/settings`: 시니어에게 `ALBUM`, `GOAL`, `HOME`, `ETC` 네 항목을 그대로 돌려주되 모두 `enabled=true`, `mandatoryProductPush=true`, `canEditProductPush=false`다. 저장된 OFF 행은 무시한다. 옛 앱이 화면을 그대로 그려도 잠긴 켜짐 스위치로 보이게 하려고 항목을 빼지 않았다.
- `PATCH /api/v1/fcm/settings`: 시니어가 네 항목 중 하나를 끄면 방장 안전 그룹과 같은 `FCM_4090`(409)이다. 켜면 저장 없이 잠긴 켜짐을 돌려준다. 두 경우 모두 행과 revision이 바뀌지 않는다. 그룹 검사(`FCM_4001`)와 revision 검사(`FCM_4091`)는 잠금 판정보다 먼저다.
- `FCM_4090`의 메시지는 「방장의 안전 알림 푸시는 끌 수 없습니다.」 그대로다. 위듀 앱은 설정 화면을 없애므로 메시지를 노출할 곳이 없고, 코드를 늘리지 않으려고 새 오류 코드를 만들지 않았다.

### 11.3 데이터

DDL 없음. 남은 시니어 설정 행은 판정에 쓰이지 않는다. `scripts/mysql/cleanup_senior_notification_settings.sql`은 시니어 설정 행을 지우는 선택 정리 스크립트다. 자동 실행하지 않고, 실행한다면 이 변경을 배포한 뒤에 백업 후 실행한다. #881의 `migrate_senior_notification_settings.sql`(옛 `GENERAL=false`를 네 OFF 행으로 복사)은 실행해도 결과가 무시되므로 더 실행할 필요가 없다.

### 11.4 인수조건

- [x] AC7: 시니어가 받는 타입 있는 푸시(예: M02)와 타입 없는 레거시 푸시가 개인 설정을 조회하지 않고 claim, preflight를 통과한다(`FcmEligibilityTest`).
- [x] AC8: 시니어 설정 조회는 OFF 행이 있어도 네 항목 모두 켜짐, 끌 수 없음이다(`NotificationSettingServiceTest`).
- [x] AC9: 시니어 OFF PATCH는 `FCM_4090`이고 revision이 그대로다. ON PATCH는 저장 없이 잠긴 켜짐이다(`NotificationSettingServiceTest`).
- [x] AC10: 보호자 조회, 변경, 발송 판정과 방장 필수 안전 그룹은 기존 테스트 그대로 통과한다.

### 11.5 앱 몫

- 위듀 앱은 알림 설정 화면을 없앤다(정책서 2절). 옛 앱은 잠긴 켜짐으로 보인다.
- 앱 로컬 「심박수 주의 알림」 스위치도 없애야 한다. 서버는 자가 확인(`SAFETY_SELF_CHECK`)을 #881 때부터 설정 없이 보내므로, 이 스위치로 자가 확인 화면이 뜨지 않는 문제는 앱 쪽에서만 생긴다.

