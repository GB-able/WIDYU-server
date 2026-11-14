# LLD-0148: JWT 종류 검증과 기동 시 비밀값 검사

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1054 |
| 관련 ADR | ADR-0002 (JWT 인증/인가) |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

서버는 액세스, 리프레시, 임시 세 종류의 JWT를 발급하고, 파서는 **서명 키로만** 종류를 가른다. 세 비밀값이 다르면 다른 종류의 토큰은 서명 검증에서 떨어지지만, 설정 실수로 비밀값이 같아지면 그대로 통과한다. 특히 소셜 임시 토큰은 `sub=memberId`, `role=USER`라서 액세스 토큰 자리에 넣으면 그 회원으로 인증된다. 운영 설정 검증 스크립트는 비밀값이 있는지만 본다.

발급할 때는 이미 서명된 헤더에 `type`(`access`, `refresh`, `temporary`)을 넣고 있으므로, 파서가 이 값을 확인하면 비밀값 설정과 관계없이 종류를 가를 수 있다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- 파서 네 개(`parseAccessToken`, `parseRefreshToken`, `parseTemporaryToken`, `parseSocialTemporaryToken`)가 헤더 `type`이 자기 종류일 때만 받는다.
- 기동 시 세 비밀값이 32바이트(HS256 키 최소 길이) 이상이고 서로 다른지 검사하고, 아니면 기동하지 않는다(`JwtProperties` 생성 시 검사).

### Out of scope
- 임시 토큰 만료 시간 단위 변경. 9절 참고. 이번에는 동작을 바꾸지 않고 현재 단위를 기록만 한다.
- `scripts/docker/validate-prod.py`에 같은 검사를 더하는 것. 애플리케이션 기동 검사가 모든 환경을 막으므로 따로 두지 않는다.
- 소셜 임시 토큰과 SMS 임시 토큰의 구분 방식 변경. 둘 다 `temporary` 종류이고 지금처럼 `role`(USER / TEMPORARY)로 가른다.

## 3. 인터페이스 / API

API 변경 없음. 응답 코드도 그대로다. 종류가 다른 토큰은 기존 "파싱 실패"와 같은 경로를 탄다.

| 자리 | 다른 종류의 토큰을 넣었을 때 |
| --- | --- |
| `Authorization: Bearer` (액세스) | 401 `INVALID_ACCESS_TOKEN` |
| 리프레시 재발급 | 기존 리프레시 파싱 실패와 같은 응답 |
| 임시 토큰 경로 | 기존 임시 토큰 파싱 실패와 같은 응답 |

## 4. 데이터 모델

변경 없음. 토큰 형식도 바꾸지 않는다.

- 헤더 `type`은 2025-08-21 JWT 첫 구현(`09e219a3`)부터 모든 발급 경로(`buildJwtToken`, `buildRefreshToken`)가 넣었다. 지금 유효한 토큰은 모두 이 헤더를 가지므로 배포 후 재로그인이 필요 없다.
- 헤더는 JWS 서명 대상이라 클라이언트가 바꾸면 서명 검증에서 떨어진다.

## 5. 처리 흐름

1. 파서가 자기 종류의 키로 서명과 만료를 검증한다(기존).
2. 헤더 `type`이 기대 종류의 값과 다르거나 없으면 `JwtException`을 던진다.
3. 각 파서는 기존처럼 만료 예외만 다시 던지고 나머지는 `null`을 돌려준다. 호출자(`JwtTokenProvider`, `TemporaryMemberUtil`)의 처리는 그대로다.

기동 시:

1. Spring이 `jwt.*` 설정으로 `JwtProperties`를 만들 때 생성자가 세 비밀값을 검사한다.
2. 비어 있거나 UTF-8 32바이트 미만이면, 또는 두 값이라도 같으면 `IllegalStateException`으로 설정 바인딩이 실패해 애플리케이션이 뜨지 않는다. 메시지에는 설정 키 이름만 넣고 비밀값은 넣지 않는다.

## 6. 예외 / 에러 처리

- 새 에러 코드 없음.
- 만료된 토큰은 종류 확인 전에 만료 예외가 나므로 기존처럼 만료 응답을 받는다. 만료된 토큰은 어느 자리에서도 인증되지 않으므로 종류를 먼저 볼 필요가 없다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 세 비밀값이 모두 같아도 소셜 임시 토큰, 리프레시 토큰을 액세스 파서에 넣으면 거부된다.
- [x] 세 비밀값이 모두 같아도 액세스 토큰을 리프레시, 임시, 소셜 임시 파서에 넣으면 거부된다.
- [x] `type` 헤더가 없는 토큰은 거부된다.
- [x] 정상 발급한 액세스 토큰은 회원과 역할을 그대로 돌려준다.
- [x] 소셜 임시 토큰은 임시 파서에서 `USER` 역할로 읽혀 SMS 임시 토큰(`TEMPORARY`)과 구분된다(`TemporaryMemberUtil`의 역할 확인 유지).
- [x] 비밀값이 같거나, 32바이트보다 짧거나, 없으면 `JwtProperties`를 만들 수 없다.
- [x] test 프로필(`application-test.yml`) 비밀값은 검사를 통과한다(40~43바이트, 서로 다름).
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 이미 발급된 토큰: 영향 없음(4절).
- 배포: 개발 서버의 세 비밀값이 서로 다르고 32바이트 이상이어야 기동한다. 32바이트 미만이면 지금도 토큰 발급 때 `WeakKeyException`이 나므로 실제로 동작 중인 서버라면 길이는 통과한다. **서로 다른지는 배포 전에 확인해야 한다.** 같으면 기동이 실패하므로 값을 바꾼 뒤 배포한다(그때 기존 토큰은 무효가 되어 재로그인이 필요하다).
- 로컬 실행: 개발자 환경변수의 세 비밀값도 같은 조건을 만족해야 한다.

## 9. 미결정 사항 (Open Questions)

- [ ] **임시 토큰 만료 시간 단위 (확인 필요, 이번 PR에서 동작 바꾸지 않음).** 액세스, 리프레시는 초 단위 설정에 1000을 곱해 쓰지만(`JwtProperties.*MilliTime`), 임시 토큰은 `jwt.temporary-token-expiration-time`을 곱하지 않고 밀리초로 그대로 쓴다. 근거 자료가 서로 다르다.
  - `application-test.yml`은 `300000`(밀리초로 5분)이다.
  - `scripts/docker/test_prod_config.py`의 예시 값은 `1800`이다. 초로 넣은 값이면 임시 JWT가 1.8초 만에 만료된다.
  - 임시 회원 Redis 기록(`TemporaryMember`)의 TTL은 1800초(30분) 고정이고, backend 지침도 임시 토큰을 30분으로 적는다.
  - 개발 서버 `JWT_TEMPORARY_TOKEN_EXPIRATION_TIME`의 실제 값은 확인하지 않았다. 지금 단위를 초로 바꾸면 이미 밀리초(예: 1800000)로 넣은 환경에서는 만료가 1000배 길어진다. 실제 값을 확인한 뒤 단위를 정하고, 바꿀 때는 환경변수 값과 코드를 함께 바꾼다.

## 10. 참고

- 이슈 #1054, 출처: 2026-11-14 저장소 점검(정적 코드 리뷰)
- `backend/widyu-api/src/main/java/com/widyu/global/util/JwtUtil.java`
- `backend/widyu-api/src/main/java/com/widyu/global/properties/JwtProperties.java`
- `backend/widyu-api/src/main/java/com/widyu/global/util/TemporaryMemberUtil.java`
- LLD-0025 (임시 토큰 인증 필터)
