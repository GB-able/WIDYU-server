# LLD-0137: 어르신 탈퇴 요청과 탈퇴 시각 기록

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1017 |
| 관련 ADR | ADR-0043(보관 기간과 탈퇴 삭제, 별도 PR) |
| 작성자 | Claude |
| 작성일 | 2026-11-12 |

## 1. 목적 / 배경

개인정보 처리방침은 어르신도 앱에서 직접 탈퇴를 시작할 수 있다고 안내한다. 지금 어르신에게는 탈퇴 수단이 없다. 앱스토어 심사도 앱 안에서 계정 삭제를 시작할 수 있어야 통과하므로 출시 전에 필요하다.

코드에서 확인한 문제는 셋이다.

1. 어르신 탈퇴 API가 없다.
2. 탈퇴 시각을 저장하지 않는다. 회원 상태 `INACTIVE`는 보호자 탈퇴(`MemberWithdrawService`)와 관리자 정지(`AdminMemberService.changeStatus`)가 함께 쓴다. 상태만으로는 둘을 가를 수 없어 「탈퇴 30일 뒤 삭제」(노션 이슈 2-2)가 대상 회원을 고를 수 없다.
3. **보호자 탈퇴 API를 어르신이 부를 수 있다.** `/api/v1/auth/**`는 `SecurityConfig`에서 `permitAll`이고, `MemberWithdrawService.withdrawMember`도 회원 유형을 보지 않는다. 어르신 토큰으로 부르면 계정이 「탈퇴회원」으로 가려지고 `INACTIVE`가 되지만, `senior_profile.family_id`는 그대로라 가족 화면에 탈퇴회원으로 남는다. 어르신 회원을 넣은 서비스 단위 테스트로 재현했다(상태 `INACTIVE`, 이름 「탈퇴회원」, 가족 연결 유지, `clearFamilyByFamilyId` 미호출).

### 어르신 탈퇴 방식: 요청 기록과 운영자 처리

설계 초안(LLD-0134, 미커밋)은 어르신 본인 즉시 탈퇴를 권장했다. 이번에는 노션 2-1의 최소 버전인 「요청을 서버에 남기고 운영자가 확인해 10일 안에 처리」로 간다.

- 즉시 탈퇴는 팀 결정이 남아 있다. 가족의 마지막 어르신이 나가면 보호자만 남은 가족방을 둘지 해체할지, 실증 참가자의 탈퇴를 연구 참여 철회로 볼지, 탈퇴 즉시 위치 기록을 지우는 것이 위치정보법의 확인자료 보존과 충돌하지 않는지가 정해지지 않았다.
- 요청 기록 방식은 이 결정들을 운영자의 사람 판단으로 미룬다. 앱스토어 요건(앱 안에서 삭제 시작)도 충족한다.
- 감수하는 점: 요청부터 처리까지 최대 10일 동안 위치 수집과 가족 열람이 이어진다. 처리방침의 「바로 지운다」(2-3)와 거리가 있고, 운영자 처리 절차가 사람 손에 달려 있다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`Member.withdrawnAt`, 탈퇴 요청 엔티티, `AdminAction` 값 2개, 오류 코드 2개), widyu-api(탈퇴 요청 API, 관리자 API, 보호자 탈퇴 유형 확인)
- `member.withdrawn_at` 칸. 보호자 탈퇴 때 채운다.
- 어르신 탈퇴 요청 API, 관리자 목록·처리 완료·취소 API
- 보호자 탈퇴 API를 보호자만 부르게 막는다.

### Out of scope
- 실제 계정 처리. 운영자가 한다. 서버는 요청과 처리 사실만 기록한다.
- 탈퇴 30일 뒤 삭제(2-2), 어르신 탈퇴 시 위치 즉시 삭제(2-3), 탈퇴자 앨범 삭제(2-4). 결정할 항목이 많다(LLD-0134 9절).
- 요청 접수를 운영자에게 푸시·디스코드로 알리기. 9절 1번.
- 어르신 본인의 요청 취소, 요청 상태 조회 API. 다시 누르면 열린 요청을 그대로 돌려받으므로 최소 버전에는 없어도 된다.
- 관리자 대시보드(admin SPA) 화면

## 3. 인터페이스 / API

### 3.1 어르신 탈퇴 요청 (신규)

```http
POST /api/v1/members/seniors/me/withdrawal-requests
Authorization: Bearer <어르신 access token>
```

요청 본문이 없다. 탈퇴 사유 같은 자유 입력은 받지 않는다(개인정보가 섞일 수 있고 운영자 처리에 필요하지 않다).

```json
{
  "code": "SENIOR_2003",
  "message": "탈퇴 요청 접수 성공",
  "data": {
    "requestId": 12,
    "memberId": 7,
    "status": "REQUESTED",
    "requestedAt": "2026-11-12T10:00:00",
    "dueAt": "2026-11-22T10:00:00",
    "processedAt": null,
    "processedByAdminId": null
  }
}
```

- 열린 요청(`REQUESTED`)이 이미 있으면 새로 만들지 않고 그 요청을 같은 형식으로 돌려준다.
- `dueAt`은 `requestedAt + 10일`이다. 저장하지 않고 응답에서 계산한다.

| 상황 | 응답 |
| --- | --- |
| 어르신이 아닌 회원 | 403 `AUTH_4030` |
| 토큰 없음·비활성 회원 | 401 (기존 필터) |

### 3.2 관리자 API (신규, `ROLE_ADMIN`)

```http
GET  /api/v1/admin/members/withdrawal-requests?status=REQUESTED&page=0&size=20
POST /api/v1/admin/members/withdrawal-requests/{requestId}/complete
POST /api/v1/admin/members/withdrawal-requests/{requestId}/cancel
```

- 목록은 `AdminPageResponse<MemberWithdrawalRequestResponse>`이고 오래된 요청부터(`id` 오름차순) 준다. `status`를 빼면 전부다. `size`는 1~100.
- 처리 완료는 운영자가 계정 처리를 마친 뒤 부른다. `PROCESSED`, 처리 시각, 처리 관리자를 남긴다.
- 취소는 운영자가 본인 확인 중 어르신이 탈퇴하지 않겠다고 한 경우에 부른다. `CANCELLED`를 남긴다.
- 응답은 3.1과 같은 항목이다. 이름·전화번호는 담지 않는다. 운영자는 `memberId`로 기존 회원 상세 화면을 연다.

| 상황 | 응답 |
| --- | --- |
| 없는 요청 | 404 `MEMBER_4043` |
| 이미 처리 완료·취소된 요청 | 409 `MEMBER_4091` |
| 관리자가 아님 | 403 (기존 `/api/v1/admin/**` 규칙) |

### 3.3 보호자 탈퇴 (변경)

`DELETE /api/v1/auth/guardians/withdraw`는 요청·응답이 그대로다. 보호자가 아닌 회원이 부르면 403 `AUTH_4030`이고 아무것도 바뀌지 않는다. 관리자 계정은 유형이 `GUARDIAN`이라 영향이 없다.

## 4. 데이터 모델

### 4.1 `member.withdrawn_at` (widyu-domain `com.widyu.member.Member`)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `withdrawn_at` | DATETIME(6) NULL | 회원이 탈퇴한 시각. 관리자 정지는 채우지 않는다 |

- `Member.markWithdrawn(LocalDateTime)`이 상태를 `INACTIVE`로 바꾸고 `withdrawn_at`을 채운다. 보호자 탈퇴가 쓴다.
- `Member.withdraw()`는 관리자 정지가 그대로 쓴다. `withdrawn_at`을 건드리지 않는다.
- `Member.reactivate()`는 `withdrawn_at`을 비운다. 관리자가 되살린 회원이 나중에 탈퇴 30일 삭제 대상이 되지 않게 한다.
- 인덱스는 이번에 만들지 않는다. 이 칸으로 고르는 조회(2-2)가 생길 때 그 LLD에서 정한다.

### 4.2 `member_withdrawal_request` (신규, widyu-domain `com.widyu.member.MemberWithdrawalRequest`)

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `id` | BIGINT PK | 요청 ID |
| `member_id` | BIGINT NOT NULL | 요청한 어르신. FK는 걸지 않는다(`social_unlink_task`와 같은 관행) |
| `status` | VARCHAR(20) NOT NULL | `REQUESTED` / `PROCESSED` / `CANCELLED`. `@JdbcTypeCode(VARCHAR)` |
| `requested_at` | DATETIME(6) NOT NULL | 요청 시각 |
| `processed_at` | DATETIME(6) NULL | 처리 완료·취소 시각 |
| `processed_by_admin_id` | BIGINT NULL | 처리한 관리자 회원 ID |
| `created_at`, `updated_at` | DATETIME(6) | `BaseTimeEntity` |

인덱스 `idx_member_withdrawal_request_member (member_id, status)`, `idx_member_withdrawal_request_status (status, id)`.

상태 전이는 `REQUESTED → PROCESSED`, `REQUESTED → CANCELLED` 둘뿐이다. 닫힌 요청은 바뀌지 않는다. 닫힌 뒤 같은 어르신이 다시 요청하면 새 행이 생긴다.

### 4.3 기타

- `AdminAction`에 `MEMBER_WITHDRAWAL_REQUEST_PROCESSED`, `MEMBER_WITHDRAWAL_REQUEST_CANCELLED`를 더한다.
- `ErrorCode`에 `MEMBER_WITHDRAWAL_REQUEST_NOT_FOUND`(404 `MEMBER_4043`), `MEMBER_WITHDRAWAL_REQUEST_CLOSED`(409 `MEMBER_4091`)를 더한다.
- DTO `member/dto/response/MemberWithdrawalRequestResponse`(widyu-api). 어르신과 관리자 응답이 같은 형식이다.

## 5. 처리 흐름

### 5.1 어르신 탈퇴 요청 (`MemberWithdrawalRequestService.request`, 한 트랜잭션)

1. 토큰의 회원 ID로 `MemberRepository.findByIdForUpdate`를 불러 회원 행을 잠근다. 같은 어르신의 동시 요청이 여기서 줄을 선다(`FollowupCardService`와 같은 방식). 잠금을 트랜잭션의 첫 읽기로 둔다. 잠금 전에 일반 읽기를 하면 MySQL REPEATABLE READ 스냅샷이 그때 잡혀, 잠금을 기다리는 동안 다른 요청이 커밋한 행을 3번에서 보지 못하고 요청이 둘 생긴다.
2. 잠근 회원이 `SENIOR`가 아니면 403.
3. 그 회원의 `REQUESTED` 요청이 있으면 그대로 돌려준다.
4. 없으면 `REQUESTED` 행을 저장하고 돌려준다. 로그는 `memberId`, `requestId`만 남긴다.

### 5.2 관리자 처리 완료·취소 (`complete`·`cancel`, 한 트랜잭션)

1. 요청 행을 `PESSIMISTIC_WRITE`로 읽는다. 없으면 404. 두 운영자가 동시에 눌러도 하나만 바뀌고 나머지는 409다.
2. `REQUESTED`가 아니면 409.
3. 상태, `processed_at`, `processed_by_admin_id`(`AdminIdentityResolver.resolveAdminId`)를 채운다.
4. 같은 트랜잭션에서 감사 로그(`AdminAuditLogService.logInCurrentTransaction`)를 남긴다. 대상 `MEMBER_WITHDRAWAL_REQUEST`, detail은 `memberId=<id>`뿐이다. 감사 로그가 실패하면 처리도 롤백된다.

### 5.3 보호자 탈퇴 (`MemberWithdrawService.withdrawMember`)

1. 현재 회원이 `GUARDIAN`이 아니면 다른 어떤 작업보다 먼저 403을 던진다.
2. 나머지는 지금과 같고, 마지막에 `member.withdraw()` 대신 `member.markWithdrawn(now)`를 부른다.

### 5.4 운영자에게 알리는 수단

기존 수단을 확인했다. 디스코드 알림(alert-relay, `docs/architecture/alerting.md`)은 개발 서버의 5xx·스케줄러·FCM 최종 실패 로그를 Grafana 규칙으로 세어 보내는 **오류 전용** 경로다. 업무 이벤트를 운영자에게 보내는 장치는 없다. 이번에는 만들지 않고, 운영자가 관리자 목록 API(대시보드 화면은 후속)로 `REQUESTED`를 확인한다. 9절 1번.

## 6. 예외 / 에러 처리

| 코드 | HTTP | 조건 |
| --- | --- | --- |
| `AUTH_4030` (`FORBIDDEN`) | 403 | 어르신 아닌 회원의 탈퇴 요청, 보호자 아닌 회원의 보호자 탈퇴 |
| `MEMBER_4043` | 404 | 없는 탈퇴 요청 ID |
| `MEMBER_4091` | 409 | 이미 처리 완료·취소된 요청을 다시 처리 |

응답과 로그에 이름·전화번호를 넣지 않는다. 로그는 `memberId`, `requestId`만 남긴다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 보호자가 탈퇴하면 상태가 `INACTIVE`이고 `withdrawn_at`이 채워진다.
- [x] 관리자가 회원을 `INACTIVE`로 바꾸면 `withdrawn_at`이 비어 있고, `ACTIVE`로 되살리면 `withdrawn_at`이 비워진다.
- [x] 어르신이 보호자 탈퇴 API를 부르면 403이고 계정 가림, 토큰 삭제, 연동 해제 예약, 탈퇴 이벤트가 하나도 일어나지 않는다.
- [x] 어르신이 탈퇴를 요청하면 `REQUESTED` 요청이 하나 저장되고 `dueAt`이 요청 시각 + 10일이다.
- [x] 열린 요청이 있는 어르신이 다시 요청하면 새 행 없이 기존 요청을 돌려받는다. 회원 행 잠금 뒤 확인한다.
- [x] 보호자가 탈퇴 요청 API를 부르면 403이고 아무것도 저장되지 않는다.
- [x] 관리자가 열린 요청을 처리 완료하면 `PROCESSED`, 처리 시각, 처리 관리자 ID가 남고 감사 로그(`MEMBER_WITHDRAWAL_REQUEST_PROCESSED`)가 생긴다. 취소도 같은 방식으로 `CANCELLED`가 남는다.
- [x] 이미 닫힌 요청을 처리하면 409, 없는 요청은 404다.
- [x] 관리자 목록은 상태로 거를 수 있고 오래된 요청부터 나온다.
- [x] 응답 DTO와 감사 로그 detail에 이름·전화번호가 없다.
- [x] Swagger에 새 API 셋과 보호자 탈퇴 403이 반영된다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- **운영 DDL (운영 구축 때 적용)**: 운영 서버는 아직 없다. 구축할 때 아래 스크립트를 적용한다.
  - `scripts/mysql/alter_member_withdrawn_at.sql`: `member.withdrawn_at` 추가와 기존 탈퇴자 백필
  - `scripts/mysql/create_member_withdrawal_request.sql`: 요청 표 생성
  - `scripts/mysql/alter_admin_audit_log_action.sql`: `admin_audit_log.action`이 MySQL ENUM이면 새 값 2개를 더한다. 빠뜨리면 감사 로그 INSERT가 실패해 처리 완료·취소가 통째로 롤백된다.
- **개발 서버**: `ddl-auto: update`가 새 칸과 표를 만든다. 다만 `admin_audit_log.action`이 ENUM이면 새 값을 더하지 않으므로, 관리자 처리 API를 쓰기 전에 위 ALTER를 개발 DB에서 실행해야 한다. 기존 탈퇴자 백필도 원하면 같은 스크립트로 한다.
- **기존 탈퇴자 백필**: 탈퇴와 관리자 정지를 「이름이 탈퇴회원이고 번호가 비어 있음」으로 가른다. 기준 시각은 `updated_at`이다(9절 3번).
- **보호자 탈퇴 동작 변경**: 어르신 토큰으로 보호자 탈퇴 API를 부르던 화면이 있으면 403이 된다. 어르신 앱은 새 탈퇴 요청 API를 써야 한다.
- **앱 작업(위듀 어르신 앱)**: 설정 화면에 「탈퇴 요청」 버튼과 확인 창, `POST /api/v1/members/seniors/me/withdrawal-requests` 호출, 접수 안내(`dueAt` 표시 가능).
- `backend/CLAUDE.md` auth 절에 보호자 전용과 어르신 탈퇴 요청을 적는다.

## 9. 미결정 사항 (Open Questions)

- [ ] 1. **요청 접수 알림**: 운영자가 관리자 목록을 주기적으로 보는 것으로 충분한가, 접수 때 디스코드 같은 알림이 필요한가. 필요하면 업무 이벤트용 알림 경로를 새로 만들어야 한다(지금 디스코드는 오류 전용).
- [ ] 2. **운영자 처리 절차**: 운영자가 어르신 계정을 실제로 어떻게 처리하는지 문서가 없다. 처리 완료 API가 서버에서 계정 가림과 `withdrawn_at` 기록까지 하게 할지는 2-2, 2-3과 함께 정한다. 그 전까지는 처리 완료가 기록일 뿐이고, 운영자가 계정을 가리지 않으면 `withdrawn_at`도 비어 있다.
- [ ] 3. **백필 기준 시각**: 기존 탈퇴자의 `withdrawn_at`을 `updated_at`으로 채울지(기본안), 적용 시각으로 채워 30일을 새로 줄지. 운영 서버가 없어 지금은 개발 DB에만 해당한다.

## 10. 참고

- 노션 「보관 기간이 지난 자료 지우기와 탈퇴 처리를 처리방침대로 맞추기」 이슈 2의 2-1
- 설계 초안: LLD-0134(어르신 즉시 탈퇴, 30일 뒤 삭제, 미커밋), ADR-0043(별도 PR)
- 기존 탈퇴 구조: LLD-0059(소셜 연동 해제, Redis 위치), LLD-0030(S3 삭제 작업), LLD-0060(비활성 회원 토큰 차단)
- 감사 로그 관행: LLD-0052(실증 참여 기록의 `logInCurrentTransaction`)
