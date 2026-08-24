# LLD-0025: 임시 토큰 인증 API의 액세스 토큰 필터 분리

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #522 |
| 관련 ADR | ADR-0002 |
| 작성자 | dongkyunKim |
| 작성일 | 2026-08-24 |

## 1. 목적 / 배경

SMS 인증 후 발급한 임시 토큰을 `Authorization: Bearer`로 전달하면 공통 `JwtAuthenticationFilter`가 이를 액세스 토큰으로 먼저 검증한다. 두 토큰은 서로 다른 서명키를 사용하므로 요청은 `AUTH_4018`로 차단되고, 회원가입 서비스의 임시 토큰 검증까지 도달하지 못한다.

임시 토큰을 소비하는 인증 API에서는 액세스 토큰 필터만 건너뛰고, 기존 서비스가 임시 토큰의 서명·만료·역할·임시 회원 존재 여부를 검증하도록 책임을 분리한다.

## 2. 범위

### In scope

- 변경 모듈: `widyu-api`
- 임시 토큰을 소비하는 기존 인증 API의 액세스 토큰 필터 제외
- 이메일 회원가입 요청이 실제 HTTP 필터 체인을 통과하는 테스트
- 일반 API의 액세스 토큰 필터 동작 보존 테스트

### Out of scope

- 토큰 발급·서명키·만료시간 변경
- `Authorization: Bearer` 이외의 신규 임시 토큰 헤더 도입
- 인증 API URL·요청·응답 변경
- `SecurityConfig`의 인가 정책 전면 개편
- 소셜·로컬 회원가입 비즈니스 로직 변경

## 3. 인터페이스 / API

기존 API 계약을 변경하지 않는다.

```http
POST /api/v1/auth/guardians/sign-up/local
Authorization: Bearer {temporaryToken}
Content-Type: application/json
```

다음 기존 API도 임시 토큰을 자체 검증하므로 동일하게 액세스 토큰 필터에서 제외한다.

```text
PATCH /api/v1/auth/guardians/password
PATCH /api/v1/auth/guardians/apple/phone-number
GET   /api/v1/auth/guardians/profile/temporary
POST  /api/v1/auth/guardians/social/integration
```

## 4. 데이터 모델

엔티티·테이블·Redis 키·토큰 claim 변경은 없다.

- `TemporaryMember`: 기존 Redis TTL과 조회 방식을 유지한다.
- Temporary Token: 기존 `TEMPORARY` 타입, 임시 토큰 서명키와 만료시간을 유지한다.
- Access Token: 기존 액세스 토큰 서명키와 인증 필터 동작을 유지한다.

## 5. 처리 흐름

### 임시 토큰 API

1. 요청이 `JwtAuthenticationFilter`에 진입한다.
2. 필터가 요청 경로를 임시 토큰 API의 명시적 목록과 비교한다.
3. 일치하면 액세스 토큰 파싱을 건너뛰고 다음 필터로 전달한다.
4. Controller와 Service가 `Authorization` 헤더에서 임시 토큰을 추출한다.
5. `TemporaryTokenService` 또는 기존 임시 토큰 유틸리티가 임시 토큰 서명과 만료를 검증한다.
6. 임시 회원 또는 소셜 임시 토큰 정보를 확인한 뒤 기존 작업을 수행한다.

### 그 외 API

1. 기존과 동일하게 `Authorization` 헤더의 Bearer 토큰을 액세스 토큰으로 파싱한다.
2. 유효하면 `SecurityContext`에 인증을 설정한다.
3. 유효하지 않으면 기존 액세스 토큰 오류를 반환한다.

## 6. 예외 / 에러 처리

- 임시 토큰 누락·형식 오류·서명 불일치: 기존 `INVALID_TEMPORARY_TOKEN`을 유지한다.
- 임시 토큰 만료: 기존 `TEMPORARY_TOKEN_EXPIRED`를 유지한다.
- 임시 회원 없음: 기존 `TEMPORARY_MEMBER_NOT_FOUND`를 유지한다.
- 일반 API의 액세스 토큰 오류: 기존 `INVALID_ACCESS_TOKEN`과 `EXPIRED_ACCESS_TOKEN`을 유지한다.

필터 제외는 임시 토큰 검증 생략이 아니다. 액세스 토큰 필터와 임시 토큰 검증의 중복 충돌만 제거한다.

## 7. 인수조건 (Acceptance Criteria)

- [x] SMS 인증으로 발급한 임시 토큰을 `Authorization: Bearer {temporaryToken}`으로 전달하면 이메일 회원가입 요청이 서비스까지 도달한다.
- [x] 이메일 회원가입 요청의 임시 토큰을 액세스 토큰으로 파싱하지 않는다.
- [x] 임시 토큰 누락·서명 오류·만료·임시 회원 부재는 기존 오류로 거절한다.
- [x] 비밀번호 변경, Apple 전화번호 보강, 임시 프로필 조회, 소셜 계정 연동도 액세스 토큰 필터와 충돌하지 않는다.
- [x] 임시 토큰 API 이외의 요청은 기존 액세스 토큰 필터를 그대로 적용한다.
- [x] 실제 Spring Security 필터 체인을 거치는 HTTP 테스트가 회원가입 필터 동작을 검증한다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- `JwtAuthenticationFilter`의 경로별 적용 여부와 관련 테스트만 변경한다.
- DB·Redis·환경변수·배포 설정 마이그레이션은 없다.
- 모바일 앱은 기존 요청 URL과 `Authorization: Bearer` 헤더를 유지한다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- Issue #522
- ADR-0002
- `JwtAuthenticationFilter`
- `TemporaryTokenService`
