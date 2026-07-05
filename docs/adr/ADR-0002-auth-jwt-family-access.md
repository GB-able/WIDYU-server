# ADR-0002: 인증/인가 전략 — JWT + @ValidateFamilyAccess AOP

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-05 |
| 관련 | ADR-0001, ERD-0001 |

## 맥락 (Context)

WIDYU는 보호자(GUARDIAN)와 시니어(SENIOR) 두 역할이 존재한다.
보호자는 자신의 가족에 속한 시니어 리소스만 접근할 수 있어야 한다.
모든 API마다 "이 보호자가 해당 시니어의 가족인가"를 수동으로 검증하면 코드 중복과 검증 누락 위험이 크다.
또한 WebSocket 연결 URL에 JWT를 포함하면 Nginx·프록시 로그에 인증 정보가 노출되는 보안 위험이 있다.

## 결정 (Decision)

**1. JWT 토큰 전략**
- Access Token: 응답 바디 → 클라이언트 메모리 보관. 만료 시간 짧게 설정.
- Refresh Token: `HttpOnly·Secure·SameSite=Strict` 쿠키. JavaScript 접근 불가 → XSS 탈취 차단.
- 소셜 로그인 Refresh Token: 각 소셜 제공자가 발급한 토큰을 `SocialAccount.refreshToken`에 저장.
- WebSocket 연결: 30초 TTL 1회용 토큰을 별도 HTTP API로 발급, Redis `GETDEL`로 원자적 소비.

**2. 가족 접근 권한 — `@ValidateFamilyAccess` AOP**
- 컨트롤러 메서드에 `@ValidateFamilyAccess(memberIdParam = "seniorId")` 선언.
- `FamilyAccessAspect(@Before)`가 호출 시점에 자동 검증:
  - 현재 인증 멤버 추출 (`MemberUtil.getCurrentMember()`)
  - 파라미터에서 `seniorId` 추출
  - `FamilyMembershipRepository`로 보호자-시니어 가족 관계 확인
  - 관계 없으면 `BusinessException(FORBIDDEN)` 던짐

**3. OAuth 인가 코드 수신**
- RFC 6749 기준: 인가 코드를 프론트가 받으면 URL에 노출. 백엔드 리다이렉트 URI로 직접 수신하도록 구조 설계.

## 고려한 대안 (Considered Options)

1. **서비스 레이어 수동 검증** — 각 서비스마다 가족 관계를 직접 조회·검증
   - 장점: 명시적
   - 단점: 반복 코드, 검증 누락 가능성

2. **`@ValidateFamilyAccess` AOP (채택)** — 어노테이션 선언만으로 검증 자동화
   - 장점: 검증 누락 방지, 컨트롤러 단에서 선언적으로 보안 적용
   - 단점: AOP 동작 이해 없이는 흐름 추적 어려움

3. **Access Token HttpOnly 쿠키 저장** — 단순하지만 CORS + 쿠키 설정 복잡도 증가
   - 단점: 단기 토큰의 쿠키 갱신 흐름 복잡, 채택하지 않음

## 결과 (Consequences)

### 긍정
- XSS로 Refresh Token 탈취 경로 차단
- 보호자-시니어 접근 권한 검증이 AOP로 자동화 → 비즈니스 로직과 보안 로직 분리
- WebSocket 1회용 토큰으로 URL 로그 노출 방지

### 부정 / 트레이드오프
- `@ValidateFamilyAccess`는 컨트롤러 파라미터 이름에 의존 → 파라미터 이름 변경 시 어노테이션 속성도 같이 변경 필요
- AOP가 적용된 메서드는 단위 테스트 시 Aspect를 직접 호출하거나 MockMvc 통합 테스트로 검증해야 함

## 후속 / 미결정
- Access Token 만료 시 자동 갱신 방식 (Silent Refresh vs 인터셉터)은 프론트와 협의 필요
