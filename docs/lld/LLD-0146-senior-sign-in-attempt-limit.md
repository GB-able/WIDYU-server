# LLD-0146: 시니어 로그인 시도 제한

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1049 |
| 관련 ADR | ADR-0002 (JWT 인증/인가) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경
시니어 로그인(`POST /api/v1/auth/seniors/sign-in`)은 인증 없이 부를 수 있고 전화번호와 7자리 숫자 초대코드만 맞으면 시니어 토큰 쌍을 준다. 보호자 로컬 로그인과 관리자 로그인에는 실패 시도 제한(LLD-0034)이 있지만 이 경로에는 없어, 시니어 번호를 아는 사람이 초대코드를 제한 없이 대입할 수 있다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- `SeniorAuthService.seniorSignIn`에 기존 로그인 제한(`AuthLimitStore.reserveLogin`/`completeLogin`)을 전화번호와 IP 기준으로 건다.
- Swagger에 429, 503 응답을 적는다.

### Out of scope
- 초대코드를 서버가 `SecureRandom`으로 만들거나 길이, 문자 집합을 늘리는 것 (앱 변경 필요)
- 시니어 로그인에 SMS 인증을 더하는 것 (앱 변경 필요)
- nginx `limit_req`
- 한도 수치의 정책 확정 (9절)

## 3. 인터페이스 / API
`POST /api/v1/auth/seniors/sign-in` 요청, 성공 응답(토큰 쌍)은 그대로다. 새로 생기는 응답은 보호자 로컬 로그인과 같다.

- 한도 초과: 429 `AUTH_4290`, `Retry-After` 헤더(초)
- 제한 저장소(Redis) 장애, 클라이언트 IP를 알 수 없음: 503 `AUTH_5030`

## 4. 데이터 모델
DB 변경 없음. Redis 키는 기존 로그인 키 공간을 그대로 쓴다.

- 계정 키: `auth:{limits}:login:account:<sha256("senior:" + 전화번호)>`. 보호자는 `id:<로컬계정ID>`/`unknown:<이메일>`을 쓰므로 겹치지 않는다.
- IP 키: `auth:{limits}:login:ip:<sha256(IP)>`. 보호자, 관리자 로그인과 **공유**한다. 한 IP가 로그인 종류를 바꿔 가며 IP 한도를 몇 배로 쓰지 못하게 하려는 선택이다.

새 설정 키는 없다. `auth.limits.login-window-seconds`(900), `login-account-failures`(10), `login-ip-failures`(100)를 그대로 쓴다.

## 5. 처리 흐름
1. `@Valid`로 형식 검증(초대코드 7자리 숫자, 전화번호 10~11자리)
2. `ClientIpResolver.resolve()`로 IP를 구하고 `reserveLogin("senior:" + 전화번호, IP)`로 예약한다. 한도에 닿아 있으면 DB를 조회하지 않고 429.
3. 초대코드와 전화번호로 시니어 프로필을 조회한다.
4. 조회가 `BusinessException`(없음 `INVITE_CODE_NOT_FOUND`, 중복 `PHONE_NUMBER_SHARED_BY_MEMBERS`)으로 끝나면 `completeLogin(false)` 후 같은 예외를 던진다.
5. 찾으면 `completeLogin(true)`로 자기 예약만 지우고 토큰 쌍을 발급한다. 앞서 쌓인 실패는 지우지 않는다(보호자 로그인과 같음).

계정 조회 전에 예약하는 점이 보호자 로그인과 다르다. 보호자는 로컬계정 ID로 키를 묶으려고 먼저 조회하지만, 시니어는 요청의 전화번호가 곧 키라서 조회 전에 막을 수 있다. 예외나 프로세스 종료로 완료하지 못한 예약은 창(900초)이 끝날 때까지 실패처럼 센다.

## 6. 예외 / 에러 처리
새 에러 코드 없음. 기존 `AUTH_RATE_LIMITED`(429), `AUTH_LIMIT_UNAVAILABLE`(503), `INVITE_CODE_NOT_FOUND`(404), `PHONE_NUMBER_SHARED_BY_MEMBERS`(409)를 쓴다.

## 7. 인수조건 (Acceptance Criteria)
- [x] 같은 전화번호로 10번 실패하면 11번째는 맞는 코드여도 DB 조회 없이 429다.
- [x] 같은 IP에서 IP 한도만큼 실패하면 다른 전화번호의 요청도 429다.
- [x] 맞는 코드의 로그인은 한도보다 많이 반복해도 매번 토큰 쌍을 받는다.
- [x] Swagger에 429, 503 응답이 반영된다.
- [x] 관련 테스트가 격리 Redis로 통과한다(`SeniorLoginLimitRedisTest`, `SeniorAuthServiceTest`).

## 8. 영향 범위 / 마이그레이션
- 클라이언트: 실패가 쌓이면 429가 새로 나온다. 앱이 429를 일반 오류로 보여 줘도 동작은 막히지 않는다.
- IP 키 공유: 가족이 같은 Wi-Fi나 통신사 NAT 뒤에 있으면 시니어 로그인 실패가 보호자 로그인의 IP 한도(15분 100회)를 함께 쓴다. 기존 보호자, 관리자 로그인이 이미 같은 키를 나눠 쓰고 있다.
- 배포: DB, 설정 변경 없음. 이 서버 버전이 배포돼야 제한이 걸린다.

## 9. 미결정 사항 (Open Questions)
- [ ] 한도 수치: 보호자 로그인 기본값(15분 창, 번호당 10회, IP당 100회)을 그대로 썼다. 기본값으로 정함, 정책 확인 필요. 시니어 초대코드는 경우의 수가 10^7이라 번호당 10회/15분이면 하루 최대 960회 시도다.
- [ ] 초대코드 생성 방식 변경, SMS 추가 여부 (앱 변경 필요, 후속)

## 10. 참고
- #1049, LLD-0034(인증·로그인 제한), ADR-0002
- 점검 근거: 2026-11-14 저장소 정적 점검(기준 커밋 `497365c4`)
