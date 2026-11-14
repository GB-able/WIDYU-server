# LLD-0145: Apple 회원 전화번호 보강을 Apple 로그인 결과와 결합

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #838 |
| 관련 ADR | ADR-0002 (JWT 인증/인가) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

Apple 로그인은 전화번호를 주지 않아서, Apple로 가입한 보호자는 로그인 뒤 SMS 인증을 거쳐 `PATCH /api/v1/auth/guardians/apple/phone-number`로 번호를 채운다. 지금 이 API는 **요청 본문의 이메일**로 번호를 채울 회원을 고른다. 요청자가 그 회원이라는 확인이 없어서, 남의 Apple 이메일을 아는 사람은 번호가 빈 그 회원에게 자기 번호를 넣을 수 있다. 전화번호는 소셜 로그인과 계정 연동에서 기존 회원을 찾는 기준이므로, 심어진 번호로 그 회원 계정의 토큰까지 얻을 수 있다.

LLD-0094는 이미 번호가 있는 회원의 덮어쓰기를 막았지만 번호가 빈 회원은 남겨 두었다(LLD-0094 Open Questions 1). Apple 로그인이 성공하면 서버는 번호가 비어 있어도 액세스 토큰을 발급하고, 보호자 앱은 그 토큰을 저장한 채 번호 단계로 넘어간다. 이 토큰으로 대상 회원을 정하면 서버가 Apple에서 직접 확인한 로그인 결과에 번호를 묶을 수 있다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 번호를 채울 회원을 `Authorization` 헤더의 **액세스 토큰** 회원으로 정한다. 요청 본문의 이메일은 받지 않는다.
- SMS 임시 토큰은 요청 본문 `temporaryToken`으로 받아 "이 번호를 SMS로 인증했다"는 증명에만 쓴다.
- `JwtAuthenticationFilter`의 임시 토큰 경로 목록에서 이 API를 빼서 액세스 토큰 검증(활성 회원 확인 포함)을 거치게 한다.
- 대상 회원에게 Apple 소셜 계정이 없으면 거부한다.

### Out of scope
- 이전 요청 형식(헤더에 임시 토큰, 본문에 이메일)의 호환 기간. 운영 서버가 아직 없어 앱과 날짜를 맞춰 한 번에 바꾼다.
- 소셜 계정 연동(`/social/integration`)의 기존 계정 소유 확인. #1050에서 다룬다.
- Apple `id_token` 서명 검증. LLD-0094와 같이 범위 밖이다.

## 3. 인터페이스 / API

```http
PATCH /api/v1/auth/guardians/apple/phone-number
Authorization: Bearer {Apple 로그인으로 받은 accessToken}
Content-Type: application/json

{ "temporaryToken": "{SMS 인증으로 받은 임시 토큰}" }
```

- `temporaryToken`: string, 필수. 비어 있으면 400.
- 응답은 기존과 같다(`AUTH_2008`, `data: null`).

변경 전:

```http
PATCH /api/v1/auth/guardians/apple/phone-number
Authorization: Bearer {temporaryToken}

{ "email": "guardian@example.com" }
```

## 4. 데이터 모델

변경 없음. DTO `auth/dto/request/AppleSignUpRequest`의 필드를 `email`에서 `temporaryToken`으로 바꾼다.

## 5. 처리 흐름

호출자 `GuardianAuthService.updatePhoneNumberIfAppleSignUp`의 트랜잭션 안에서 `SocialLoginService.updatePhoneNumberIfAppleSignUp`이 처리한다.

1. `JwtAuthenticationFilter`가 `Authorization`의 액세스 토큰을 검증하고 활성 회원이면 인증 정보를 설정한다. 토큰이 액세스 토큰이 아니거나 만료됐으면 필터가 401로 끝낸다. 헤더가 없으면 인증 없이 통과한다(`/api/v1/auth/**`는 `permitAll`).
2. `TemporaryMemberUtil.getTemporaryMember(request.temporaryToken())`로 임시 토큰과 임시 회원을 확인한다. 실패하면 회원을 조회하지 않는다.
3. `MemberUtil.getCurrentMember()`로 액세스 토큰의 회원을 읽는다. 인증 정보가 없으면 `UNAUTHORIZED`.
4. 회원에게 `provider=APPLE` 소셜 계정이 없으면 `FORBIDDEN`.
5. 다른 회원이 임시 회원의 번호를 쓰고 있으면 `ALREADY_REGISTERED_PHONE_NUMBER`(LLD-0116, 기존과 같음).
6. `fillPhoneNumberIfEmpty` 조건부 UPDATE로 빈 번호만 채운다. 0행이면 `FORBIDDEN`(LLD-0094, 기존과 같음).
7. 임시 회원 삭제를 커밋 뒤로 등록한다(LLD-0094, 기존과 같음).

## 6. 예외 / 에러 처리

| 상황 | 에러 |
| --- | --- |
| `temporaryToken` 누락·빈 값 | 요청 검증 실패 (400) |
| `Authorization`이 액세스 토큰이 아님·만료 (예: 이전 앱이 임시 토큰을 헤더에 보냄) | 필터 401 |
| 임시 토큰 형식 오류·역할 불일치 | UNAUTHORIZED (401) |
| 임시 토큰 만료 | TEMPORARY_TOKEN_EXPIRED (401) |
| 임시 토큰의 임시 회원 없음 | MEMBER_NOT_FOUND (404) |
| `Authorization` 헤더 없음 | UNAUTHORIZED (401) |
| 액세스 토큰 회원에게 Apple 계정 없음 | FORBIDDEN (403) |
| 다른 회원이 쓰는 번호 | ALREADY_REGISTERED_PHONE_NUMBER (400) |
| 이미 번호가 있는 회원 | FORBIDDEN (403) |

새 에러 코드는 없다.

## 7. 인수조건 (Acceptance Criteria)

- [ ] AC1: 액세스 토큰 회원이 번호가 빈 Apple 회원이면 임시 토큰의 번호로 채우고 커밋 뒤 임시 회원을 삭제한다.
- [ ] AC2: 번호를 채울 회원은 액세스 토큰으로만 정해진다. 요청 본문으로 대상 회원을 고를 수 없다(이메일 필드 없음).
- [ ] AC3: 액세스 토큰이 없으면 번호를 바꾸지 않고 UNAUTHORIZED로 실패한다.
- [ ] AC4: 임시 토큰이 유효하지 않으면 회원을 조회하지 않고 실패한다.
- [ ] AC5: 액세스 토큰 회원에게 Apple 계정이 없으면 FORBIDDEN이고 번호와 임시 회원이 바뀌지 않는다.
- [ ] AC6: 이미 번호가 있는 회원, 다른 회원이 쓰는 번호는 기존과 같이 거부한다.
- [ ] AC7: 인증 필터가 이 API의 액세스 토큰을 검증하고, 비활성 회원 토큰은 401로 막는다.
- [ ] AC8: Swagger 설명과 요청 예시가 새 요청 형식과 맞다.
- [ ] AC9: `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- **클라이언트(보호자 앱) 변경 필요:** `AuthService.updateApplePhone`이 `TokenType.access`로 호출하고, 저장된 SMS 임시 토큰을 본문 `temporaryToken`에 넣고, `email`을 빼야 한다. 이전 앱은 헤더의 임시 토큰이 액세스 토큰 검증에 걸려 401이 된다. 운영 서버가 없으므로 개발 서버에 서버를 먼저 올리고 앱과 날짜를 맞춘다.
- **문서 정정:** LLD-0004 §3·§5의 전화번호 보강 요청 형식(헤더 임시 토큰, 본문 이메일)과 LLD-0094 §3·§5-2 1~2단계, LLD-0025의 임시 토큰 경로 목록 중 이 API는 이 문서가 대체한다.
- 스키마 변경 없음.

## 9. 미결정 사항 (Open Questions)

- 없음. 앱 반영 날짜는 배포 조율 사항이며 설계를 바꾸지 않는다.
