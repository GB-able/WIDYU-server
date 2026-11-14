# LLD-0147: 가족 참여 코드 시도 제한

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1051 |
| 관련 ADR | ADR-0002 (JWT 인증/인가, 가족 접근) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경
가족이 없는 보호자는 6자리 가족 코드(`[A-Z0-9]`)만 맞으면 승인 없이 그 가족의 구성원이 되고, 방장이 없으면 방장이 된다. 성공 뒤에는 시니어의 위치, 심박, 위급 기록, 앨범에 접근한다. 지금은 실패해도 같은 계정으로 계속 시도할 수 있어 가족이 늘수록 대입이 쉬워진다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- `FamilyConnectionService.joinFamily`(`POST /api/v1/members/guardians/family/join`)에 회원 기준, IP 기준 실패 시도 제한을 건다.
- `AuthLimitStore`에 로그인과 키를 나눈 `reserveFamilyJoin`을 더한다.
- Swagger에 429, 503 응답을 적는다.

### Out of scope (앱 변경 필요, 후속)
- 방장 승인 후 참여
- 만료되는 1회용 초대
- 가족 코드 길이, 문자 집합 변경
- 한도 수치의 정책 확정 (9절)

## 3. 인터페이스 / API
요청, 성공 응답은 그대로다. 새로 생기는 응답은 로그인 제한과 같은 형식이다.

- 한도 초과: 429 `AUTH_4290`, `Retry-After` 헤더(초)
- 제한 저장소(Redis) 장애, 클라이언트 IP를 알 수 없음: 503 `AUTH_5030`

## 4. 데이터 모델
DB 변경 없음. Redis 키는 로그인과 같은 해시 태그(`auth:{limits}:`) 아래 다른 이름을 쓴다.

- 회원 키: `auth:{limits}:family-join:member:<sha256(회원ID)>`
- IP 키: `auth:{limits}:family-join:ip:<sha256(IP)>`

로그인 키(`login:account`, `login:ip`)와 섞이지 않아, 같은 IP의 로그인 실패가 가족 참여를 막거나 그 반대가 되지 않는다. 예약, 완료 Lua 스크립트(`auth-login-reserve`, `auth-login-complete`)는 그대로 재사용한다.

새 설정 키는 없다. 창과 한도는 `auth.limits.login-window-seconds`(900), `login-account-failures`(10, 여기서는 회원당), `login-ip-failures`(100)를 같이 쓴다.

## 5. 처리 흐름
1. `MemberUtil.getCurrentMember()`로 인증된 회원을 얻는다. 보호자가 아니면 403(예약 전).
2. `ClientIpResolver.resolve()`로 IP를 구하고 `reserveFamilyJoin(회원ID, IP)`로 예약한다. 한도에 닿아 있으면 가족 코드를 조회하지 않고 429.
3. 기존 참여 로직(코드 조회 → 이미 소속 확인 → 멤버십 저장 → 순서 revision 증가 → 시니어 목록 조회)을 그대로 실행한다.
4. 3이 `BusinessException`(코드 없음 `INVITE_CODE_NOT_FOUND`, 이미 소속 `ALREADY_CONNECTED_TO_FAMILY`)으로 끝나면 `completeLogin(false)` 후 같은 예외를 던진다.
5. 성공하면 `completeLogin(true)`로 자기 예약만 지운다.

IP는 로그인과 같이 서비스에서 `ClientIpResolver`로 얻는다. `AuthLimitWebConfig`의 Tomcat valve가 모든 요청에 원래 peer를 남기므로 이 경로에도 적용된다. 이미 소속된 보호자의 요청도 실패로 센다. 코드 조회가 소속 확인보다 앞이라 이미 소속된 보호자는 404와 400으로 코드 존재를 가릴 수 있기 때문이다.

## 6. 예외 / 에러 처리
새 에러 코드 없음. 기존 `AUTH_RATE_LIMITED`(429), `AUTH_LIMIT_UNAVAILABLE`(503)을 쓴다.

## 7. 인수조건 (Acceptance Criteria)
- [x] 같은 보호자가 틀린 코드를 10번 보내면 11번째는 코드 조회 없이 429다.
- [x] 같은 IP에서 여러 보호자의 실패가 IP 한도에 닿으면 다른 보호자도 429다.
- [x] 같은 IP의 로그인 실패가 로그인 IP 한도에 닿아도 가족 참여는 막히지 않는다(키 분리).
- [x] 기존 참여 동작(정렬 순서, revision, 예외)이 그대로다(`FamilyConnectionServiceTest`).
- [x] Swagger에 429, 503 응답이 반영된다.

## 8. 영향 범위 / 마이그레이션
- 클라이언트: 실패가 쌓이면 429가 새로 나온다.
- Redis가 내려가면 가족 참여가 503이다(로그인과 같은 fail closed).
- 배포: DB, 설정 변경 없음.

## 9. 미결정 사항 (Open Questions)
- [ ] 한도 수치: 로그인 기본값(15분 창, 회원당 10회, IP당 100회)을 그대로 썼다. 기본값으로 정함, 정책 확인 필요. 가족 참여는 정상 사용자가 몇 번 틀릴 일이 드물어 더 낮춰도 된다.
- [ ] 방장 승인, 만료되는 1회용 초대, 코드 길이 변경 여부 (앱 변경 필요, 후속)

## 10. 참고
- #1051, LLD-0034(인증·로그인 제한), LLD-0146(시니어 로그인 시도 제한), ADR-0002
- 점검 근거: 2026-11-14 저장소 정적 점검(기준 커밋 `497365c4`)
