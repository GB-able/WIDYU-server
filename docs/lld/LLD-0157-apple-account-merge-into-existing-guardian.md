# LLD-0157: 기존 보호자가 Apple로 로그인하면 Apple 계정을 기존 회원에 합친다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1075 |
| 관련 ADR | ADR-0002 (JWT 인증/인가) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

이메일과 비밀번호로 가입한 보호자가 나중에 Apple로 로그인하면, Apple은 전화번호를 주지 않고 Apple 이메일은 기존 회원의 소셜 이메일과 맞지 않아 서버가 기존 회원을 찾지 못한다. 그래서 Apple 로그인은 빈 새 회원을 만들고 토큰을 준다. 번호 등록 단계(LLD-0145)에서 SMS로 인증한 번호의 주인이 기존 보호자로 드러나지만, 지금은 400(`AUTH_4021`)으로 끝나고 앱이 시도하는 소셜 연동 API는 SMS 임시 토큰을 받지 않아 사용자가 진행할 수 없다. 2026-11-14 개발 서버에서 재현됐다.

SMS 인증은 이 서비스에서 이미 계정 주인 확인으로 쓰인다(비밀번호 재설정). 같은 증명으로 Apple 계정을 번호 주인에게 옮기면 사용자가 기존 계정으로 진행할 수 있고, 계정 탈취 위험은 비밀번호 재설정 이상으로 늘지 않는다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- `PATCH /api/v1/auth/guardians/apple/phone-number`에서 인증한 번호의 주인이 다른 회원이면, 요청한 Apple 회원의 Apple 계정을 그 회원으로 옮기고 Apple 회원은 탈퇴 처리한 뒤 그 회원의 토큰을 돌려준다.
- 응답 본문에 합쳤는지 여부와 토큰을 싣는다(`ApplePhoneNumberResponse`).

### Out of scope
- Apple 외 제공자(카카오, 네이버)의 같은 상황. 두 제공자는 로그인 때 전화번호를 줘서 서버가 기존 회원을 바로 찾는다.
- 이미 쌓인 빈 Apple 회원 정리. 사용자가 다시 Apple 로그인하면 그 빈 회원으로 로그인되고, 번호 등록 단계에서 이 흐름으로 합쳐진다.
- 소셜 연동 API(`/social/integration`)의 소유 확인(#1050).

## 3. 인터페이스 / API

요청은 LLD-0145와 같다(헤더 액세스 토큰, 본문 `temporaryToken`). 응답 `data`가 생긴다.

```json
{ "code": "AUTH_2008", "message": "애플 로그인 회원 전화번호 업데이트 성공",
  "data": { "merged": true, "memberId": 9, "accessToken": "...", "refreshToken": "..." } }
```

- `merged=false`: 번호만 채웠다. 토큰 필드는 없다(`NON_NULL`). 앱은 기존 토큰을 그대로 쓴다.
- `merged=true`: 기존 회원에 합쳤다. 앱은 받은 토큰으로 바꿔 저장하고 기존 회원으로 진행한다. 이전 토큰(빈 회원)은 탈퇴 회원이라 필터가 401로 막는다.

## 4. 데이터 모델

스키마 변경 없음.
- `MemberRepository.moveSocialAccount(source, target, provider)`: `UPDATE SocialAccount SET member = :target WHERE member = :source AND provider = :provider`. `Member.socialAccounts`가 `orphanRemoval`이라 컬렉션에서 빼서 옮기면 행이 지워지므로 UPDATE 한 문장으로 바꾼다. `(provider, oauth_id)` 유일 키는 그대로 유지된다.
- DTO `auth/dto/response/ApplePhoneNumberResponse`.

## 5. 처리 흐름

`GuardianAuthService.updatePhoneNumberIfAppleSignUp` 트랜잭션 안.

1. 임시 토큰과 임시 회원 확인, 액세스 토큰 회원(A) 확인, A에게 Apple 계정이 없으면 403 (LLD-0145와 같음).
2. A에게 이미 번호가 있으면 403.
3. `findTop2ByPhoneNumber(인증한 번호)`: 둘 이상이면 409 `MEMBER_4090`. 없으면 기존처럼 빈 번호를 채우고 `merged=false`.
4. 주인(B)이 있으면 `AppleAccountMergeService.merge(A, B)`:
   1. A가 빈 회원인지 확인: 보호자, 로컬 계정 없음, 소셜 계정은 Apple 하나뿐, 회원의 기록이 없음(`MemberRepository.countOwnedContentFlag`). 회원 ID를 담는 컬럼이 있는 테이블(외래키 유무와 무관, 2026-11-14 dev 스키마 기준 37개 컬럼) 중 시스템 행(알림 `fcm_notification`, `fcm_outbox`, `member_fcm_token`, `member_notification_setting`, 로그인 계정 `local_account`, `social_account`, 작업 큐 `s3_object_deletion_task`, `medication_proof_image_deletion_task`, `social_unlink_task`, 동의 기록 `consent_record`)과 관리자 기록(`admin_access_log`, `admin_audit_log`, 탈퇴 요청의 처리 관리자)을 뺀 전부를 본다. 아니면 400 `AUTH_4021`(기록이 있는 회원을 탈퇴 처리하면 그 기록에 닿을 수 없다). 회원 ID 컬럼을 가진 테이블이 새로 생기면 이 목록에 더한다. 미머지 PR #1069의 `senior_stay_state`(시니어 체류 상태)는 이 PR 시점에 테이블이 없어 넣지 않았고, 시니어 기록이라 보호자 빈 회원 판정에는 영향이 없다.
   2. B 행을 `entityManager.refresh(B, PESSIMISTIC_WRITE)`로 잠그며 최신 상태로 다시 읽는다. 번호가 인증한 번호와 달라졌으면(조회와 잠금 사이 번호 변경) 403. 활성 보호자(탈퇴, 정지 아님)가 아니면 400 `AUTH_4021`. B에게 Apple 계정이 이미 있으면 400 `AUTH_4019`(동시 합치기도 잠금 뒤 여기서 걸린다).
   3. `moveSocialAccount(A, B, APPLE)`가 1행이 아니면 403(동시 요청이 먼저 옮김).
   4. 영속성 컨텍스트가 비워졌으므로 A를 다시 읽어 `maskPersonalInfo()`, `markWithdrawn(now)`. A의 리프레시 토큰을 지운다. Apple 연동 해제(토큰 폐기)는 하지 않는다. 계정이 B로 옮겨갔기 때문이다.
   5. B의 토큰 쌍(`loginType=APPLE`)을 발급한다.
5. 임시 회원은 커밋 뒤 삭제한다(LLD-0094와 같음). `merged=true`와 B의 토큰을 돌려준다.

외래키가 없는 기록 중 동의 기록(`consent_record`)은 보지 않는다. 앱이 가입 중에 동의를 먼저 기록하면 정상 흐름까지 막히고, 그 기록은 빈 회원 자신의 동의 이력이라 탈퇴 회원에 그대로 남긴다. 보호자는 합친 뒤 자기 계정의 동의 상태를 쓴다.

FCM 토큰은 따로 옮기지 않는다. 앱이 로그인 뒤 토큰을 등록하면 `upsertActive`가 현재 회원(B)으로 옮긴다.

## 6. 예외 / 에러 처리

| 상황 | 에러 |
| --- | --- |
| A에게 이미 번호가 있음 | FORBIDDEN (403) |
| 같은 번호의 회원이 둘 이상 | PHONE_NUMBER_SHARED_BY_MEMBERS (409) |
| A가 빈 회원이 아님(로컬 계정, 다른 소셜, 직접 만든 자료) | ALREADY_REGISTERED_PHONE_NUMBER (400) |
| 잠금 뒤 B의 번호가 인증한 번호와 다름 | FORBIDDEN (403) |
| B가 시니어, 탈퇴, 정지 | ALREADY_REGISTERED_PHONE_NUMBER (400) |
| B에게 이미 Apple 계정이 있음 | SOCIAL_PROVIDER_ALREADY_LINKED (400) |
| 옮길 Apple 계정이 없음(동시 요청) | FORBIDDEN (403) |

새 에러 코드는 없다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1: 번호 주인이 활성 보호자이고 A가 빈 Apple 회원이면 Apple 계정이 B로 옮겨지고 `merged=true`와 B의 토큰을 돌려준다.
- [x] AC2: 옮긴 Apple 계정은 지워지거나 가려지지 않고, 같은 Apple ID로 로그인하면 B가 찾아진다.
- [x] AC3: A는 탈퇴 처리되고(개인정보 가림, 탈퇴 시각) 리프레시 토큰이 지워진다. Apple 연동 해제는 일어나지 않는다.
- [x] AC4: B가 시니어, 탈퇴 회원이거나 이미 Apple 계정이 있으면 합치지 않는다.
- [x] AC5: A에 자료(가족 소속, 주소 즐겨찾기 등 직접 만든 자료, 다른 소셜 계정)가 있으면 합치지 않는다.
- [x] AC5-1: B를 잠그고 다시 읽었을 때 번호가 인증한 번호와 다르면 합치지 않는다.
- [x] AC6: 번호 주인이 없으면 기존처럼 번호만 채우고 `merged=false`다.
- [x] AC7: Swagger 설명과 응답 예시가 반영된다.
- [x] AC8: `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- **클라이언트(보호자 앱) 변경 필요:** Apple 번호 단계에서 `GET /profile/temporary`로 연동 화면에 보내지 말고 바로 이 API를 부른다. 응답 `merged=true`면 받은 토큰을 저장하고 기존 회원으로 진행, `false`면 지금처럼 다음 가입 단계로 간다. 응답 본문이 `null`에서 객체로 바뀌지만, 지금 앱은 상태 코드만 보므로 이 PR만으로 깨지지 않는다.
- 탈퇴 처리된 빈 회원은 관리자 통계의 탈퇴 회원에 포함된다.
- 스키마 변경 없음.

## 9. 미결정 사항 (Open Questions)

- 없음. 방향은 사용자가 확정했다(2026-11-14).
