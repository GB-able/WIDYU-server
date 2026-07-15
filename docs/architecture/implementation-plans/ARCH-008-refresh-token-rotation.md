# ARCH-008 Refresh Token Rotation 구현 설계

## 1. 문제 정의

### 현재 확인된 동작

- `UnifiedAuthController.reissueTokenPair()`는 `GuardianAuthService.reissueTokenPair()`를 호출한다.
- `GuardianAuthService.reissueTokenPair()`는 `GuardianTokenService.reissueTokenPair()`로 위임한다.
- `GuardianTokenService.reissueTokenPair()`는 `JwtTokenProvider.retrieveRefreshToken(request.refreshToken())`로 토큰을 파싱하고 Redis 키 존재 여부만 확인한다.
- `JwtTokenProvider.retrieveRefreshToken()`은 `RefreshTokenRepository.findById(parsed.memberId())`가 존재하는지만 본다.
- Redis에 저장된 `RefreshToken.token` 값과 요청 refresh token 값은 비교하지 않는다.
- `GuardianTokenService.reissueTokenPair()`는 `jwtTokenProvider.createRefreshTokenDto(memberId)`로 새 refresh token을 한 번 저장한 뒤, `jwtTokenProvider.generateTokenPair()`를 호출해 refresh token을 다시 생성·저장한다.

### 보안상 문제

- 재발급으로 refresh token이 회전된 뒤에도, 이전 refresh token의 JWT 서명이 유효하고 해당 `memberId`의 Redis 키가 존재하면 이전 토큰 재사용이 가능하다.
- 중복 저장 경로 때문에 어떤 refresh token 값이 실제 응답값인지 추적하기 어렵고, 테스트로 저장 횟수를 보장하기 어렵다.

### 깨진 불변식

- Redis에 저장된 최신 refresh token 값과 요청 refresh token 값이 정확히 일치할 때만 재발급되어야 한다.
- 재발급 성공 시 Redis에는 새 refresh token 하나만 최신값으로 저장되어야 한다.
- 회전 이전 refresh token은 즉시 재사용 불가능해야 한다.

### 재현 가능한 시나리오

1. 로그인 또는 기존 재발급으로 refresh token A가 Redis에 저장된다.
2. refresh token A로 재발급해 refresh token B가 발급되고 Redis 값이 B로 바뀐다.
3. 공격자 또는 오래된 클라이언트가 refresh token A를 다시 `/api/v1/auth/reissue`에 제출한다.
4. 현재 구현은 A의 서명이 유효하고 Redis에 같은 `memberId` 키가 있으면 통과시킬 수 있다.
5. 기대 동작은 Redis 저장값 B와 요청값 A가 다르므로 `INVALID_REFRESH_TOKEN` 실패다.

## 2. 목표

### 변경 후 보장할 불변식

- 요청 refresh token을 파싱한 뒤, 파싱된 `memberId`로 Redis 저장값을 조회한다.
- Redis 저장값이 없으면 실패한다.
- Redis 저장값의 `token`과 요청 refresh token 값이 다르면 실패한다.
- 재발급 성공 시 `JwtTokenProvider.generateTokenPair()`를 통해 access token과 새 refresh token을 생성하고, refresh token 저장은 한 번만 수행한다.
- 새 refresh token 저장이 성공하면 이전 refresh token은 Redis 최신값 비교에서 실패한다.

### 기존에 유지할 동작

- 로그인·회원가입·소셜 로그인·시니어 가입에서 `JwtTokenProvider.generateTokenPair()`를 통해 token pair를 발급하는 흐름은 유지한다.
- `/api/v1/auth/reissue` 요청·응답 DTO 형식은 유지한다.
- `/api/v1/auth/logout`은 현재 회원의 refresh token Redis 키를 삭제하는 멱등 동작을 유지한다.
- `BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)` 기반 실패 규칙을 유지한다.
- admin refresh도 같은 `JwtTokenProvider.retrieveRefreshToken()` 검증 강화를 자연스럽게 적용받는다.

### 변경하지 않을 범위

- Refresh Token 쿠키 전달 방식 변경 없음.
- 단일 세션·다중 세션 정책 변경 없음. 현재처럼 `memberId`를 Redis key로 쓰는 회원당 최신 refresh token 1개 정책을 유지한다.
- JWT 알고리즘, 만료 시간, claim 구조 변경 없음.
- RedisHash 위치와 Repository 패키지 이동 없음.
- auth 패키지 전체 구조 개편 없음.

## 3. 현재 호출 흐름

### 로그인

`GuardianAuthController 또는 관련 로그인 Controller → GuardianAuthService.localGuardianSignIn() → LocalLoginService.signIn() → JwtTokenProvider.generateTokenPair() → JwtTokenProvider.generateAndSaveRefreshToken() → RefreshTokenRepository.save()`

확인한 메서드:

- `LocalLoginService.signIn(LocalGuardianSignInRequest)`
- `JwtTokenProvider.generateTokenPair(Long, MemberRole, String)`
- `JwtTokenProvider.generateAndSaveRefreshToken(Long)`
- `JwtTokenProvider.saveRefreshTokenToStorage(Long, String)`
- `RefreshTokenRepository.save(RefreshToken)`

### 재발급

`UnifiedAuthController.reissueTokenPair() → GuardianAuthService.reissueTokenPair() → GuardianTokenService.reissueTokenPair() → JwtTokenProvider.retrieveRefreshToken() → RefreshTokenRepository.findById() → JwtTokenProvider.createRefreshTokenDto() → RefreshTokenRepository.save() → JwtTokenProvider.generateTokenPair() → RefreshTokenRepository.save()`

확인한 메서드:

- `UnifiedAuthController.reissueTokenPair(RefreshTokenRequest)`
- `GuardianAuthService.reissueTokenPair(RefreshTokenRequest)`
- `GuardianTokenService.reissueTokenPair(RefreshTokenRequest)`
- `JwtTokenProvider.retrieveRefreshToken(String)`
- `JwtTokenProvider.createRefreshTokenDto(Long)`
- `JwtTokenProvider.generateTokenPair(Long, MemberRole, String)`

문제 지점:

- `retrieveRefreshToken()`이 Redis 저장값을 비교하지 않는다.
- `createRefreshTokenDto()`와 `generateTokenPair()`가 refresh token을 각각 저장한다.

### 로그아웃

`UnifiedAuthController.logout() → GuardianAuthService.logout() → LogoutService.logout() → MemberUtil.getCurrentMember() → RefreshTokenRepository.deleteById()`

확인한 메서드:

- `UnifiedAuthController.logout()`
- `GuardianAuthService.logout()`
- `LogoutService.logout()`
- `RefreshTokenRepository.deleteById(Long)`

## 4. 변경 설계

### 요청 Refresh Token 검증 위치

- `JwtTokenProvider.retrieveRefreshToken(String refreshTokenValue)`에서 수행한다.
- 이 메서드는 guardian refresh와 admin refresh가 공통으로 사용하는 검증 관문이다.

### Redis 저장값 비교 위치

- `retrieveRefreshToken()` 내부에서 파싱 후 `RefreshTokenRepository.findById(refreshTokenDto.memberId())`로 저장값을 조회한다.
- 저장된 `RefreshToken.getToken()`과 요청 `refreshTokenValue`를 비교한다.
- 불일치하면 `BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)`을 던진다.

### 새로운 토큰 생성 시점

- `GuardianTokenService.reissueTokenPair()`에서 기존 token 검증과 회원 조회가 끝난 뒤 `JwtTokenProvider.generateTokenPair(member.getId(), MemberRole.USER, loginType)`를 호출한다.
- `createRefreshTokenDto()`는 재발급 흐름에서 호출하지 않는다.

### 이전 토큰 무효화 시점

- 별도 delete를 추가하지 않는다.
- `generateTokenPair()`가 `RefreshTokenRepository.save()`로 같은 `memberId` 키의 저장값을 새 refresh token으로 덮어쓴다.
- 이후 이전 refresh token은 Redis 저장값 비교에서 실패한다.

### Redis 저장 횟수

- 로그인 또는 신규 token pair 발급: `generateTokenPair()` 내부에서 1회 저장.
- 재발급 성공: `generateTokenPair()` 내부에서 1회 저장.
- 재발급 실패: 저장하지 않음.

### 예외 타입 및 HTTP 응답

- 기존 `BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)`을 유지한다.
- HTTP 응답 매핑은 기존 글로벌 예외 처리 정책을 따른다.
- 이번 PR에서 `ErrorCode`를 추가하거나 응답 형식을 변경하지 않는다.

### 트랜잭션 또는 원자성 고려사항

- `RefreshTokenRepository`는 Redis `CrudRepository`이며 `memberId`를 key로 사용한다.
- 비교와 저장은 엄밀한 compare-and-set 원자 연산은 아니다.
- 이번 PR은 현재 구조의 회원당 최신 token 1개 정책에서 이전 token 재사용을 차단하는 최소 변경이다.
- 동시 재발급 두 요청이 같은 최신 refresh token으로 동시에 들어오는 경우, 둘 다 검증을 통과한 뒤 마지막 저장값이 이길 수 있다. 이 동시성 정책은 이번 `ARCH-008` 범위 밖이며 별도 단일 세션·재발급 CAS 정책 후보로 남긴다.

## 5. 변경 대상 파일

### `backend/widyu-api/src/main/java/com/widyu/global/security/JwtTokenProvider.java`

- **현재 책임:** JWT 생성·파싱, refresh token Redis 저장·조회 검증.
- **변경할 내용:** `retrieveRefreshToken()`에서 Redis 저장 객체를 조회하고 저장된 token 값과 요청값을 비교한다. 저장값 부재와 값 불일치는 모두 `INVALID_REFRESH_TOKEN`으로 처리한다.
- **변경 이유:** refresh token 회전 이후 이전 token 재사용을 차단하기 위해 검증 관문을 강화한다.
- **수정하지 않아야 할 내용:** JWT claim 구조, 만료 시간, 알고리즘, access/temporary/social temporary token 로직.

### `backend/widyu-api/src/main/java/com/widyu/auth/application/guardian/GuardianTokenService.java`

- **현재 책임:** refresh token으로 guardian token pair를 재발급하고 회원 계정 타입에 맞는 `loginType`을 결정한다.
- **변경할 내용:** `createRefreshTokenDto()` 호출을 제거하고, 검증된 기존 refresh token의 `memberId`로 회원을 조회한 뒤 `generateTokenPair()`만 호출한다.
- **변경 이유:** 재발급 성공 시 refresh token 저장을 한 번만 수행하게 한다.
- **수정하지 않아야 할 내용:** `loginType` 결정 방식의 의미(local 우선 → social provider → "unknown"), 응답 형식, member 조회 방식.
- **허용되는 부수 변경 (Fable 결정, 2026-07-15):** 기존 loginType 판별의 삼항 연산자는 프로젝트 규칙(삼항 연산자 금지)과 파일 편집 하네스 검사에 걸리므로, 판별 의미를 바꾸지 않는 조건에서 if/else 또는 early return으로 변환하는 것을 허용한다.

### `backend/widyu-api/src/test/java/com/widyu/global/security/JwtTokenProviderTest.java`

- **현재 책임:** JWT token provider의 예외 경로 단위 테스트.
- **변경할 내용:** Redis key는 존재하지만 저장된 token 값이 요청 token과 다르면 실패하는 테스트, 저장된 최신 token이면 성공하는 테스트, 만료 token 실패 테스트를 보강한다.
- **변경 이유:** 저장값 비교 불변식을 단위 테스트로 보호한다.
- **수정하지 않아야 할 내용:** access token, temporary token 테스트 의미.

### `backend/widyu-api/src/test/java/com/widyu/auth/application/guardian/GuardianTokenServiceTest.java`

- **현재 책임:** guardian refresh 재발급의 loginType 결정과 token provider 호출 검증.
- **변경할 내용:** 재발급 성공 시 `createRefreshTokenDto()`를 호출하지 않고 `generateTokenPair()`만 호출하는지 검증한다. 기존 정상 흐름과 local/social loginType 테스트를 새 흐름에 맞게 갱신한다.
- **변경 이유:** 중복 저장 경로 제거를 테스트로 보호한다.
- **수정하지 않아야 할 내용:** local/social provider 판정 테스트 의도.

### `backend/widyu-api/src/test/java/com/widyu/auth/application/LogoutServiceTest.java`

- **현재 책임:** 로그아웃 시 현재 회원 refresh token 삭제 검증.
- **변경할 내용:** 기존 테스트 유지. 필요 시 로그아웃 후 재사용 실패는 provider 단위 또는 Redis 통합 테스트에서 확인한다.
- **변경 이유:** 로그아웃 정상 동작 회귀 확인.
- **수정하지 않아야 할 내용:** 로그아웃 멱등성 기대값.

### Refresh Token Redis 통합 테스트 신규 파일 후보

- **파일 경로 후보:** `backend/widyu-api/src/test/java/com/widyu/global/security/JwtTokenProviderRedisTest.java`
- **현재 책임:** 없음. 신규 테스트.
- **변경할 내용:** 실제 Redis가 있을 때 refresh token 저장, 회전, 이전 token 재사용 실패, 로그아웃 삭제 후 실패를 검증한다. Redis가 없으면 `WsTokenServiceRedisTest`와 동일하게 `assumeTrue`로 건너뛴다.
- **구성 방법:** Spring Context 없이 `LettuceConnectionFactory("localhost", 6379)`로 연결하고, `RedisTemplate` → `RedisKeyValueAdapter` → `RedisKeyValueTemplate` → `RedisRepositoryFactory.getRepository(RefreshTokenRepository.class)`로 실제 RedisHash 기반 Repository를 만든다. `JwtUtil`은 Mock으로 두고 `generateRefreshToken`/`parseRefreshToken`/`getRefreshTokenExpirationTime`만 stub한다 — 검증 대상은 Redis 저장값 회전·비교이지 JWT 서명이 아니며, 실제 시크릿을 테스트에 두지 않는다. 토큰 값은 `"refresh-token-1"` 같은 더미 문자열을 사용한다. `@AfterEach`에서 사용한 `refreshToken:{memberId}` 키를 삭제해 테스트 간 상태 공유를 막는다.
- **변경 이유:** `TEST-001`이 Redis 통합 테스트로 정의되어 있고, Mock만으로 Redis key/value/TTL 저장 회귀를 충분히 보호할 수 없다.
- **수정하지 않아야 할 내용:** WebSocket token 테스트, Redis 설정 파일.

## 6. 테스트 설계

### 1. 저장된 최신 Refresh Token으로 재발급에 성공한다

- **테스트 위치:** `JwtTokenProviderTest`, `GuardianTokenServiceTest`
- **테스트 유형:** 단위 테스트
- **Mock 사용 여부:** `RefreshTokenRepository`, `JwtUtil`, `MemberUtil` Mock
- **Redis 통합 테스트 필요 여부:** 보조적으로 통합 테스트에서도 포함
- **기대 결과:** `retrieveRefreshToken()`이 `RefreshTokenDto`를 반환하고, `GuardianTokenService.reissueTokenPair()`가 `generateTokenPair()` 결과를 반환한다.

### 2. 회전 이전 Refresh Token으로 재발급하면 실패한다

- **테스트 위치:** 신규 `JwtTokenProviderRedisTest`
- **테스트 유형:** Redis 통합 테스트
- **Mock 사용 여부:** 없음 또는 최소 수동 구성
- **Redis 통합 테스트 필요 여부:** 필요
- **기대 결과:** token A로 재발급 후 token B가 저장되면, token A를 다시 `retrieveRefreshToken()`에 넣었을 때 `INVALID_REFRESH_TOKEN`으로 실패한다.

### 3. 다른 회원의 Refresh Token으로 재발급하면 실패한다

- **테스트 위치:** `JwtTokenProviderTest`
- **테스트 유형:** 단위 테스트
- **Mock 사용 여부:** `JwtUtil`, `RefreshTokenRepository` Mock
- **Redis 통합 테스트 필요 여부:** 선택
- **기대 결과:** 요청 token의 파싱 결과 `memberId`에 해당하는 Redis 저장값이 없거나 저장값이 다른 token이면 `INVALID_REFRESH_TOKEN`으로 실패한다. `/reissue`는 인증 컨텍스트 없이 refresh token 자체를 credential로 사용하므로 현재 사용자와의 비교는 이번 PR 범위에 포함하지 않는다.

### 4. Redis 키는 존재하지만 저장된 토큰 값과 요청 토큰 값이 다르면 실패한다

- **테스트 위치:** `JwtTokenProviderTest`
- **테스트 유형:** 단위 테스트
- **Mock 사용 여부:** `RefreshTokenRepository.findById()`가 다른 token 값을 가진 `RefreshToken` 반환
- **Redis 통합 테스트 필요 여부:** 보조적으로 통합 테스트 가능
- **기대 결과:** `BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)`.

### 5. 재발급 성공 시 Redis에는 새 토큰만 저장된다

- **테스트 위치:** 신규 `JwtTokenProviderRedisTest`
- **테스트 유형:** Redis 통합 테스트
- **Mock 사용 여부:** 없음 또는 실제 `JwtUtil` 사용
- **Redis 통합 테스트 필요 여부:** 필요
- **기대 결과:** 재발급 후 Redis key의 `token`은 응답 refresh token과 일치하고 이전 token은 검증 실패한다.

### 6. 재발급 과정에서 Refresh Token 저장은 한 번만 수행된다

- **테스트 위치:** `GuardianTokenServiceTest`
- **테스트 유형:** 단위 테스트
- **Mock 사용 여부:** `JwtTokenProvider` Mock
- **Redis 통합 테스트 필요 여부:** 불필요
- **기대 결과:** `createRefreshTokenDto()`는 호출되지 않고 `generateTokenPair()`는 한 번 호출된다.

### 7. 만료된 Refresh Token은 실패한다

- **테스트 위치:** `JwtTokenProviderTest`
- **테스트 유형:** 단위 테스트
- **Mock 사용 여부:** `JwtUtil.parseRefreshToken()`이 `ExpiredJwtException` throw
- **Redis 통합 테스트 필요 여부:** 불필요
- **기대 결과:** 기존 정책대로 `BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)`.

### 8. 로그아웃 후 Refresh Token 재사용은 실패한다

- **테스트 위치:** 신규 `JwtTokenProviderRedisTest` 및 기존 `LogoutServiceTest`
- **테스트 유형:** Redis 통합 테스트 + 단위 테스트
- **Mock 사용 여부:** `LogoutServiceTest`는 Mock, Redis 통합 테스트는 실제 Redis
- **Redis 통합 테스트 필요 여부:** 필요
- **기대 결과:** 삭제 후 `retrieveRefreshToken()`이 `INVALID_REFRESH_TOKEN`으로 실패한다.

### 9. 기존 로그인 정상 흐름은 유지된다

- **테스트 위치:** `LocalLoginServiceTest`, 필요 시 `JwtTokenProviderTest`
- **테스트 유형:** 단위 테스트
- **Mock 사용 여부:** 기존 Mock 유지
- **Redis 통합 테스트 필요 여부:** 선택
- **기대 결과:** 로그인 성공 시 `generateTokenPair()` 호출과 token pair 반환 흐름이 유지된다.

## 7. 구현 순서

> **현황 (2026-07-15):** 작업 트리에 이미 미커밋 테스트 수정이 존재한다 — `JwtTokenProviderTest`에 저장값 일치/불일치/타회원/만료/저장 1회 테스트 5개 추가, `GuardianTokenServiceTest`에 `createRefreshTokenDto` 미호출 검증 반영. Sonnet-Test는 이를 삭제하지 말고 검수·보완하며, 누락된 Redis 통합 테스트를 신규 작성한다.

1. 테스트를 먼저 보강한다.
2. `JwtTokenProviderTest`에 저장값 일치 성공, 저장값 불일치 실패, 만료 refresh token 실패를 추가한다.
3. `GuardianTokenServiceTest`에서 `createRefreshTokenDto()` 호출 기대를 제거하고 호출되지 않음을 검증한다.
4. Refresh Token Redis 통합 테스트를 추가해 회전 이전 token 재사용 실패와 로그아웃 후 재사용 실패를 고정한다.
5. 구현 전 실패하는 테스트를 확인한다.
6. `JwtTokenProvider.retrieveRefreshToken()`에 Redis 저장값 비교를 추가한다.
7. `GuardianTokenService.reissueTokenPair()`에서 `createRefreshTokenDto()` 호출을 제거한다.
8. 관련 테스트와 auth 테스트를 실행한다.
9. Living Document의 ARCH-008, TEST-001, TASK-001, PR 1 상태를 갱신한다.

## 8. 완료 조건

### 테스트 통과 조건

- `GuardianTokenServiceTest` 통과.
- `JwtTokenProviderTest` 통과.
- Refresh Token Redis 통합 테스트 통과 또는 Redis 미기동 환경에서 명시적으로 skip.
- `LogoutServiceTest`, `LocalLoginServiceTest`, `GuardianAuthServiceTest`, `AdminAuthServiceTest` 등 관련 auth 테스트 통과.

### 빌드 통과 조건

- `./gradlew :backend:widyu-api:test --tests ...`로 ARCH-008 관련 테스트 통과.
- 가능하면 `./gradlew :backend:widyu-api:test` 통과.
- 가능하면 전체 `./gradlew test` 또는 전체 빌드 통과.

### 문서 갱신 조건

- `docs/architecture/WIDYU_ARCHITECTURE_REVIEW_AND_REFACTORING_PLAN.md`에서 `ARCH-008`, `TEST-001`, `TASK-001`, PR 1 상태를 갱신한다.
- 결정 기록에 실제 적용한 Redis 저장값 비교 정책, 저장 횟수, 이전 token 차단 방식을 남긴다.
- ADR-0002는 쿠키 전달 방식 불일치가 남아 있으므로 이번 PR에서 수정하지 않았음을 기록한다.

### 회귀 여부 확인 조건

- 로그인 정상 흐름 유지.
- 재발급 정상 흐름 유지.
- 로그아웃 삭제 흐름 유지.
- public API 응답 형식 변경 없음.
- 민감한 token 값 로그 출력 없음.

## 9. 위험과 롤백

### 예상 위험

- 기존에 이전 refresh token을 재사용하던 클라이언트는 재발급 실패를 받는다. 이는 의도된 보안 변경이다.
- 동시 재발급 요청의 CAS 수준 원자성은 이번 PR에서 보장하지 않는다. 마지막 저장값이 최신 token이 된다.
- `createRefreshTokenDto()`의 운영 코드 호출처는 grep 확인 결과 `GuardianTokenService.reissueTokenPair()` 하나뿐이다. 다만 이번 PR에서는 호출 제거만 수행하고 메서드는 삭제하지 않는다. `GuardianTokenServiceTest`가 `verify(never())` 회귀 가드로 이 메서드를 참조하며, 미사용 메서드 삭제는 범위 밖 정리 후보로 Living Document에 기록한다.

### 기존 클라이언트 영향

- 요청/응답 DTO와 URL은 그대로다.
- 이전 token 재사용 시 기존보다 엄격하게 실패한다.
- Refresh Token 쿠키 전환은 하지 않으므로 클라이언트 저장 방식 변경은 없다.

### 롤백 방법

- `JwtTokenProvider.retrieveRefreshToken()`의 저장값 비교 변경을 되돌린다.
- `GuardianTokenService.reissueTokenPair()`의 `createRefreshTokenDto()` 제거 변경을 되돌린다.
- 테스트 추가분은 함께 되돌린다.
- 롤백 시 ARCH-008 보안 리스크가 다시 열리므로 Living Document 상태도 되돌려야 한다.

## 10. 제외 범위

- Refresh Token 쿠키 전달 방식 변경.
- 단일 세션·다중 세션 정책 변경.
- JWT 알고리즘 변경.
- 인증 패키지 전체 재구성.
- Redis 모델 모듈 이동.
- 다른 `ARCH` 이슈 수정.
- 관련 없는 코드 포맷팅.

## 11. Fable 자체 검증

- **설계와 실제 코드 일치:** 확인한 실제 메서드 `UnifiedAuthController.reissueTokenPair`, `GuardianAuthService.reissueTokenPair`, `GuardianTokenService.reissueTokenPair`, `JwtTokenProvider.retrieveRefreshToken`, `JwtTokenProvider.generateTokenPair`, `LogoutService.logout`, `RefreshTokenRepository.findById/save/deleteById` 기준으로 작성했다.
- **존재하지 않는 메서드·파일 가정 여부:** 운영 코드 변경 대상은 존재 확인 완료. Redis 통합 테스트 파일은 신규 후보로 명시했다.
- **TEST-001 시나리오 포함 여부:** 요청된 9개 시나리오를 모두 6장에 포함했다.
- **ARCH-008 범위 준수:** 저장값 비교와 중복 저장 제거만 다루며 쿠키, 세션 정책, JWT 알고리즘, 패키지 재구성은 제외했다.
