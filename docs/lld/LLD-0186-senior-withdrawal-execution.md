# LLD-0186: 어르신이 앱에서 바로 탈퇴하고 위치 기록을 즉시 지운다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1157 |
| 관련 ADR | ADR-0043 결정 8(어르신 탈퇴 때 위치 즉시 삭제, 실증 참가자 포함), 후속 7(어르신 탈퇴 방식, 2026-11-23 기획 결정) |
| 관련 LLD | LLD-0137(탈퇴 요청과 처리 기록, 이 문서가 일부 대체), LLD-0059(탈퇴 이벤트), LLD-0060(활성 회원 확인), LLD-0087(비상 수신자 스냅샷), LLD-0187(탈퇴 30일 뒤 삭제) |
| 작성자 | Claude |
| 작성일 | 2026-11-23 |

## 1. 목적 / 배경

어르신은 앱에서 탈퇴를 「요청」만 할 수 있었고, 운영자가 관리자 API로 처리 완료를 눌러야 했다(LLD-0137). 처리방침은 「어르신도 앱에서 직접 탈퇴할 수 있다, 어르신이 탈퇴하면 위치정보와 위치 열람 기록은 바로 지운다」고 약속한다.

2026-11-23 기획 결정(사용자 전달)으로 어르신 탈퇴를 본인 즉시 탈퇴로 바꾼다.

1. **본인 확인**: 로그인된 어르신 앱에서 누른 것을 본인 요청으로 본다. 보호자 탈퇴와 같은 기준이다. 확인 화면 두 번(「탈퇴하면 알림이 멈추고 가족이 더 이상 볼 수 없어요. 되돌릴 수 없어요」 → 큰 「탈퇴할게요」 버튼)은 앱 화면이 맡는다. 서버는 구현하지 않는다.
2. **유예 없음**: 누르는 즉시 수집과 보호자 열람을 멈추고 위치 원본과 위치 열람 기록을 지운다. 나머지 개인 자료는 30일 뒤 삭제(LLD-0187)가 맡는다.
3. **보호자 알림**: 처리되는 즉시 가족방 보호자 모두에게 「○○ 님이 위듀를 탈퇴하셔서 안부 알림이 멈췄어요」를 보낸다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`SeniorProfile.leaveFamily`, `NotificationType.SENIOR_WITHDRAWN`, 탈퇴 요청 엔티티와 오류 코드 삭제), widyu-api(어르신 탈퇴 API, `MemberWithdrawService.withdrawSenior`, 탈퇴 알림 리스너, `MemberWebSocketSessions`, 보호자 홈 카드와 가족 관리의 가족 없음 처리, 요청·관리자 API 삭제, Flyway V6)
- 어르신 본인 즉시 탈퇴 API.
- 탈퇴한 어르신의 실시간 연결(STOMP)을 커밋 뒤 닫는다.
- 가족 보호자 탈퇴 알림(D01).
- 위치 열람 기록과 위치 원본 즉시 삭제(실증 참가자 포함).
- 운영자 처리 요청 기록(`member_withdrawal_request`)과 그 API 삭제.

### Out of scope
- 확인 화면 두 번(앱 작업, 9절 클라이언트 전달 사항).
- 탈퇴 30일 뒤 개인 자료 삭제(LLD-0187), 보관 기간 정리(LLD-0136).
- 30일 안 복구. 없다(기획자 답변).
- 탈퇴 사유 입력. 보호자 탈퇴와 달리 받지 않는다(어르신 화면을 단순하게 둔다).

## 3. 인터페이스 / API

### 3.1 어르신 탈퇴 (신규) `DELETE /api/v1/members/seniors/me`

- 인증: 어르신 액세스 토큰. 요청 본문 없음.
- 응답 200: `{"code": "SENIOR_2003", "message": "탈퇴가 완료되었습니다", "data": null}` 형태(보호자 탈퇴 `AUTH_2012`와 같은 빈 본문).
- 403 `AUTH_4030`: 어르신이 아닌 회원.
- 탈퇴 뒤 같은 토큰의 요청은 `JwtAuthenticationFilter`가 비활성 회원(401 `AUTH_4020`)으로 거절한다. 같은 어르신이 동시에 두 번 눌러 두 번째가 회원 행 잠금 뒤에 들어오면 아무것도 하지 않고 200이다.

**경로 선택**: 기존 `POST /api/v1/members/seniors/me/withdrawal-requests`를 즉시 실행으로 바꾸지 않고 새 경로를 둔다. 「요청」이라는 이름과 응답(`dueAt`, 상태)이 즉시 탈퇴와 맞지 않는다. 앱 위듀 내정보에는 탈퇴 메뉴가 없어(`apiDocs/product/as-built/widyu.md`) 기존 경로를 부르는 클라이언트가 없으므로 지워도 깨지는 앱이 없다. 보호자 탈퇴 `DELETE /api/v1/auth/guardians/withdraw`와 같은 `DELETE`지만, `/api/v1/auth/**`는 `permitAll`이라 서비스가 유형을 따로 확인해야 해서 인증이 걸리는 `/api/v1/members/seniors/**` 아래에 둔다.

### 3.2 지운 API
- `POST /api/v1/members/seniors/me/withdrawal-requests`
- `GET /api/v1/admin/members/withdrawal-requests`, `POST .../{requestId}/complete`, `POST .../{requestId}/cancel`

운영자가 처리할 일이 없어졌다. 탈퇴 사실과 시각은 `member.withdrawn_at`이 남긴다. 요청 기록을 감사 목적으로 남길 이유가 없어 표도 지운다(4절). 관리자 대시보드에는 이 API를 쓰는 화면이 없다.

## 4. 데이터 모델

- `member_withdrawal_request` 표 삭제: Flyway `V6__drop_member_withdrawal_request.sql`. 엔티티, 상태 enum, 저장소, 오류 코드 2개(`MEMBER_4043`, `MEMBER_4091`)도 지웠다. 앱이 요청 API를 부른 적이 없어 운영 행이 없다.
- `AdminAction.MEMBER_WITHDRAWAL_REQUEST_PROCESSED`, `_CANCELLED`는 남긴다. 옛 감사 로그 행이 이 값을 담고 있어 지우면 읽기가 깨진다.
- `SeniorProfile.leaveFamily()`: `family`를 null로 둔다. 방장 보호자가 탈퇴해 가족이 해체될 때와 같은 상태다.
- `NotificationType.SENIOR_WITHDRAWN`: 5절.
- 저장소 삭제 쿼리: `LocationAccessLogRepository.deleteBySeniorMemberId`, `LocationFixRepository.deleteByMemberId`(JPQL 벌크 DELETE, 영속성 컨텍스트를 비우지 않는다. 비우면 같은 트랜잭션의 미반영 변경이 버려진다).

## 5. 처리 흐름

### 5.1 어르신 탈퇴 (`MemberWithdrawService.withdrawSenior`, 한 트랜잭션)

1. 현재 회원 ID로 회원 행을 `PESSIMISTIC_WRITE`로 읽는다(트랜잭션의 첫 읽기). 어르신이 아니면 403. 이미 `withdrawn_at`이 있으면 끝낸다.
2. **보호자 알림 접수**: 가족을 끊기 전에 그 가족의 보호자 목록, 이름을 가리기 전에 어르신 이름을 잡아 `SeniorWithdrawnEvent`를 낸다. 같은 트랜잭션의 리스너가 보호자마다 outbox에 D01을 접수하고, 발송은 커밋 뒤 outbox가 한다. 탈퇴가 롤백되면 알림도 남지 않는다.
3. **가족에서 뺀다**: `senior_profile.family_id = null`. 커밋 뒤 이 어르신의 비상 수신자 스냅샷 키를 지운다.
4. **위치 열람 기록과 위치 원본을 지운다.** 실증 참가자도 예외 없다(2026-11-23 기획자 답변).
5. **계정 정리**(보호자 탈퇴와 같은 `eraseAccount`): 복약 인증 사진, 리프레시 토큰, 소셜 연동 해제 작업, 이름과 번호 가림, `INACTIVE`와 `withdrawn_at`, 프로필 사진 삭제 작업, `MemberWithdrawnEvent`.
6. 커밋 뒤 `MemberWithdrawnEvent`로 Redis 실시간 위치를 지우고(`WithdrawnMemberLocationListener`) 열린 STOMP 연결을 닫는다(`MemberWebSocketSessions`).

### 5.2 수집과 열람이 바로 멈추는 근거

| 경로 | 막는 곳 |
| --- | --- |
| 어르신 REST(센서 묶음 재전송, 기기 상태, 심박 REST 등) | `JwtAuthenticationFilter`가 요청마다 활성 회원을 확인한다(LLD-0060). 탈퇴 커밋 뒤 첫 요청부터 401 `AUTH_4020` |
| 어르신 새 STOMP 연결 | 일회용 WS 토큰은 REST로 받으므로 발급이 막히고, 핸드셰이크도 활성 회원을 다시 확인한다 |
| 어르신의 이미 열린 STOMP 연결(위치, 심박, 센서 SEND) | **SEND 프레임은 활성 여부를 보지 않는다.** 그래서 커밋 뒤 그 회원의 연결을 닫는다(`MemberWebSocketSessions`, 연결이 열릴 때 핸드셰이크의 회원 ID로 등록). 탈퇴 커밋 전에 핸드셰이크를 통과한 연결이 닫기 뒤에 등록돼도 닫힌다(닫은 탈퇴 회원 ID를 기억하고 등록 때 확인) |
| 보호자의 REST 조회(위치, 심박, 목표, 홈 카드) | 가족 확인(`FamilyAccessService`, `@ValidateFamilyAccess`)이 시니어 프로필의 가족으로 관계를 보므로 가족이 없으면 403. 보호자 홈 카드(`GuardianHomeService`)와 가족 관리의 어르신 삭제(`GuardianMyPageService`)는 가족을 직접 읽다가 null에서 500이 났던 곳이라 403으로 막았다 |
| 탈퇴 전에 시작한 보호자 위치 조회의 열람 기록 | 열람 기록 저장(`LocationAccessLogService.record`, `REQUIRES_NEW`)이 어르신 회원 행을 공유 잠금으로 읽어 탈퇴(쓰기 잠금)와 줄 선다. 탈퇴가 먼저 커밋했으면 기록도 통보도 남기지 않는다. 그렇지 않으면 탈퇴 트랜잭션이 지운 뒤에 기록이 다시 생길 수 있다 |
| 보호자의 실시간 구독 | 새 구독은 가족 확인에서 거절된다. 이미 구독한 연결은 응답 채널 인터셉터(`FamilyTopicOutboundInterceptor`)가 메시지마다 활성 가족 관계를 확인하고, 어르신 연결이 닫혀 보낼 메시지도 없다 |
| 어르신에게 가는 푸시 | outbox 접수와 발송이 수신자 활성 여부를 확인한다(`FcmOutboxService`, `FcmEligibility`) |

**SEND마다 활성 확인을 넣지 않은 이유**: 위치는 회원마다 초 단위로 들어오고 위치 경로는 커넥션 풀 교착을 여러 번 겪었다(LLD-0114, 0140, 0152). 탈퇴는 드물고 한 번 연결을 닫으면 다시 열 수 없으므로 탈퇴 시점에 닫는 쪽이 비용이 작다. 연결 목록은 인스턴스 메모리라 단일 인스턴스 전제다(ADR-0023). 여러 대로 늘리면 탈퇴 이벤트를 모든 인스턴스에 알려야 한다.

### 5.3 보호자 알림 `SENIOR_WITHDRAWN`(D01)

| 항목 | 값 | 이유 |
| --- | --- | --- |
| 문구 | 제목 「{시니어 이름} 님이 위듀를 탈퇴하셔서 안부 알림이 멈췄어요」, 본문 없음 | 기획 원문. 이름은 공통 규칙대로 20글자를 넘으면 줄인다 |
| 설정 그룹 | `NONE`(끌 수 없음) | 심박, 위치 안부 알림이 끊긴 사실이다. 보호자가 이 스위치를 꺼 두면 앞으로 알림이 오지 않는 이유를 모른다. 위치 조회 통보(L01)처럼 새 스위치를 만들지 않고 끌 수 없게 둔다. 보호자 스위치 14개(LLD-0175)는 그대로다 |
| 알림센터 칩 | 기타(`ETC`) | 위치 칩은 위치 화면으로 가는 알림이다. 이 알림은 갈 화면이 없다 |
| 전달 | 푸시와 알림센터, 보통 우선순위(`INTERACTION`), 90일 보관 | 즉시 대응이 필요한 위급 알림은 아니다 |
| 딥링크 | 없음 | 탈퇴한 어르신 화면은 403이다 |
| 수신자 | 탈퇴 직전 그 가족의 보호자 전원(방장 포함) | 기획 원문 「가족방 보호자 모두」. 같은 가족의 다른 어르신은 받지 않는다 |

### 5.4 가족 처리의 선택

- **채택: 가족 연결만 끊는다(`family_id = null`).** 기존 코드에 있는 상태라(방장 보호자 탈퇴) 조회 코드가 null 가족을 처리한다. 시니어 프로필 행은 포인트 이력 FK 때문에 남기고, 30일 뒤 삭제(LLD-0187)가 지운다.
- 기각: 보호자가 어르신을 가족에서 빼는 경로처럼 프로필 행을 바로 지운다. 30일 뒤 삭제가 할 일을 앞당긴다.
- 가족의 마지막 어르신이 탈퇴해도 가족방은 남는다. 보호자는 새 어르신을 등록할 수 있다.

## 6. 예외 / 에러 처리

- 알림 접수, 삭제, 계정 정리 중 예외는 트랜잭션 전체를 롤백한다. 어르신은 다시 누를 수 있다.
- 커밋 뒤 작업(연결 닫기, 스냅샷 삭제, Redis 위치 삭제, 소셜 연동 해제)의 실패는 WARN만 남긴다. 연결 닫기에 실패해도 그 연결로 재연결은 못 하고, REST는 막혀 있다.
- 로그에는 회원 ID와 지운 행 수만 남긴다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 어르신이 `DELETE /api/v1/members/seniors/me`를 부르면 `INACTIVE`, `withdrawn_at`, 이름 「탈퇴회원」, 번호 null이 된다.
- [x] 같은 트랜잭션에서 가족 보호자 전원에게 D01 알림이 접수되고, 이름은 가리기 전 이름이다. 30글자 이름은 20글자로 줄어든다.
- [x] 가족 연결이 끊기고 커밋 뒤 비상 수신자 스냅샷이 지워진다.
- [x] 위치 열람 기록과 위치 원본이 지워지고 다른 어르신의 행은 남는다. 벌크 DELETE가 같은 트랜잭션의 미반영 변경을 버리지 않는다.
- [x] 탈퇴 이벤트가 커밋되면 그 회원의 열린 WebSocket 연결만 닫힌다. 닫기 뒤에 늦게 등록된 연결도 닫힌다.
- [x] 어르신이 이미 탈퇴했으면 보호자 조회의 열람 기록과 통보를 남기지 않는다(회원 행 공유 잠금으로 탈퇴와 줄 선다).
- [x] 가족이 없는 어르신의 보호자 홈 카드는 403이다.
- [x] 보호자 ID로 부르면 403이고 아무것도 바뀌지 않는다. 두 번째 요청이 잠금 뒤에 오면 아무것도 바뀌지 않는다.
- [x] 보호자 탈퇴 테스트가 그대로 통과한다.
- [x] Flyway V1~V6가 MySQL 8에 적용되고 엔티티 검증을 통과한다.

## 8. 영향 범위 / 마이그레이션

- **Flyway V6**: `member_withdrawal_request` 삭제.
- **API 변경**: 요청 API와 관리자 API 3개 삭제, 어르신 탈퇴 API 신규.
- **되돌릴 수 없다**: 누르는 즉시 탈퇴와 위치 기록 삭제가 일어난다.
- `backend/CLAUDE.md` auth, fcm, location.access 절과 ERD를 갱신한다. LLD-0137은 일부 대체됨으로 표시한다.

## 9. 클라이언트 전달 사항 (위듀 어르신 앱)

- 내정보에 탈퇴 메뉴를 만들고 확인 화면 두 번을 띄운다: 1) 「탈퇴하면 알림이 멈추고 가족이 더 이상 볼 수 없어요. 되돌릴 수 없어요」 2) 큰 「탈퇴할게요」 버튼.
- 확정하면 `DELETE /api/v1/members/seniors/me`(본문 없음)를 부른다. 200이면 로컬 토큰과 저장 자료를 지우고 첫 화면으로 보낸다.
- 탈퇴 직후 서버가 실시간 연결을 닫는다. 재연결을 시도하지 말고, 이후 응답의 비활성 회원 오류(401 `AUTH_4020`)도 로그아웃으로 처리한다.
- 예전 `POST /api/v1/members/seniors/me/withdrawal-requests`는 없어졌다.
- 위듀케어(보호자) 앱: 새 알림 종류 `SENIOR_WITHDRAWN`이 기타 칩에 온다. 딥링크가 없다. 탈퇴한 어르신을 홈 카드에서 고르면 403이 온다.

## 10. 미결정 사항 (Open Questions)

- [ ] 1. 위치정보법의 열람 기록 6개월 이상 보존과 탈퇴 즉시 삭제의 관계(ADR-0043 후속 1). 사용자 결정으로 즉시 지우고, 법무 확인을 기다린다.

닫은 항목: 실증 참가자의 위치 원본(2026-11-23 기획자 답변, 참가자도 지운다), 마지막 어르신 탈퇴 때 가족방(남긴다), 어르신 탈퇴 방식(본인 즉시 탈퇴).

## 11. 참고

- `MemberWithdrawService`, `SeniorController`, `SeniorWithdrawnNotificationListener`, `MemberWebSocketSessions`, `GuardianHomeService`
- 테스트: `MemberWithdrawServiceTest`, `SeniorWithdrawnNotificationListenerTest`, `MemberWebSocketSessionsTest`, `GuardianHomeServiceTest`, `SeniorWithdrawalLocationDeletionTest`, `NotificationTypeTest`
