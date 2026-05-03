# backend/CLAUDE.md

백엔드 상세 설계 문서입니다. 공통 규칙은 루트 [CLAUDE.md](../CLAUDE.md)를 참조하세요.

## Global Package Structure

`com.widyu.global` 패키지의 공통 인프라:

| 패키지 | 역할 |
|--------|------|
| `config/` | Spring 설정 (Security, WebSocket, S3, Redis 등) |
| `security/` | JWT 인증, 필터, UserDetailsService |
| `websocket/` | WebSocket 설정, JWT 핸드셰이크·채널 인터셉터 |
| `aspect/` | AOP (`@ValidateFamilyAccess`, 로깅 등) |
| `error/` | 글로벌 예외 처리, 커스텀 예외 |
| `filter/` | 서블릿 필터 (CORS, 로깅) |
| `util/` | 유틸 클래스 (날짜, 전화번호, 파일) |
| `properties/` | `@ConfigurationProperties` YAML 바인딩 |
| `infrastructure/` | 외부 서비스 클라이언트 (S3, FCM, OAuth, SMS) |

## Core Domain Modules

### `auth` — 인증
- Multi-provider OAuth (Apple, Naver, Kakao) + 로컬 SMS 인증
- 토큰 3종: Access Token, Refresh Token, Temporary Token (회원가입 플로우용)
- **SMS 인증 플로우**: SMS 발송 → 인증코드 검증 → Temporary Token 발급 → 회원가입 → JWT 발급

### `member` — 회원
- `Member` 엔티티 + `MemberType` enum (SENIOR/GUARDIAN), `MemberRole` enum (ADMIN/USER/TEMPORARY)
- `SeniorProfile`: 코드 2종 보유
  - `inviteCode` (7자): 보호자가 시니어에 연결할 때 입력, 방장(시니어 본인)만 수정 가능
  - `familyCode` (6자): 가족 코드 (별도 용도)
- `FamilyConnection`: 보호자-시니어 연결
- `PointHistory`: 포인트 적립(EARN) / 사용(USE) 내역 기록

### `mypage` — 마이페이지
- 시니어/보호자 마이페이지 분리: `SeniorMyPageService`, `GuardianMyPageService`, `MyPageProfileService`
- 프로필 수정, 포인트 내역, 가족 정보 등 조회

### `album` — 앨범
- 사진/영상 CRUD + S3 업로드
- `AlbumLike`, `AlbumComment` (2단계: 댓글+답글), `AlbumView`, `AlbumUnlock`
- FFmpeg 영상 썸네일 생성
- **비동기 업로드 파이프라인**: photos 즉시 업로드(sync) → videos `@Async` 백그라운드 처리

### `fcm` — 푸시 알림
- `@EventListener` 기반 이벤트 아키텍처
- `MemberNotificationSetting`: 카테고리별 알림 ON/OFF 설정 (회원당 카테고리 unique)
- `FcmCategory` 카테고리: ALL, ALBUM, TARGET, HEALTH_SCHEDULE, WALK, MEDICINE_SCHEDULE, HEART_MESSAGE, SAFE_ZONE
- 이벤트 리스너: `album`, `goal/walk`, `goal/healthschedule`, `medicineschedule`, `safezone`
- 비활성 유저 스케줄 알림 (3/5/7일)

### `pay` — 결제·포인트
- 포인트 기반 잠금해제 (앨범당 50포인트)
- 시니어 가입 시 100포인트 지급, `PointHistory`로 내역 기록
- TossPayments 연동

### `goal` — 건강 목표
- `medicine`: 복약 스케줄 + 알람 기반 인증
- `walk`: 걷기 추적 (`SeniorProfile.defaultWalkGoal`로 기본 목표 설정)
- `healthschedule`: 건강 스케줄 관리
- `addressbookmark`: 주소 북마크
- `home`: 목표 홈 화면 — 가족 멤버 목록 조회 (`GoalHomeService`)

### `heart` — 심박수 (독립 도메인)
- 심박수 이상치 감지 AI 연동 (`HeartRateAnomalyDetector`)
- **REST + WebSocket 이중 지원**: `HeartRateController`(REST) + `HeartRateWebSocketController`(WebSocket)
- **WebSocket 플로우**:
  - 시니어 → `/app/heart-rate/send` (15개 값 전송)
  - 보호자 구독 → `/topic/heart-rate/{memberId}` (분석 결과 브로드캐스트)
  - 발신자 ACK → `/user/queue/heart-rate/result`
- 심박수 이상 감지 시 보호자 FCM 알림 + `HeartRateEmergency` 기록
- `HeartMessageService`: 심박수 기반 메시지 서비스

### `location` — 실시간 위치
- `realtime`: WebSocket 엔드포인트 (실시간 위치 업데이트·궤적)
- `parentlocation`: 시니어 프로필 위치 관리 (REST)
- STOMP: 시니어 발신 → 보호자 구독

## Key Architectural Patterns

### Facade 패턴
```java
// AlbumFacade: S3 업로드, 썸네일, FCM 알림 조합
// HealthScheduleFacade: 건강 스케줄 관련 서비스 조합
```

### Strategy + Factory (OAuth)
```java
SocialLoginStrategy strategy = strategyFactory.getStrategy(provider); // APPLE/KAKAO/NAVER
strategy.login(request);
```

### AOP 기반 인가
```java
@ValidateFamilyAccess(memberIdParam = "memberId")
public ResponseEntity<?> getWalkDetail(@RequestParam Long memberId) {
    // 자동으로 보호자-시니어 family connection 검증
}
```

### Event-Driven (앨범 → FCM)
```java
// AlbumService → applicationEventPublisher.publishEvent(new AlbumLikedEvent(...))
// AlbumNotificationListener @EventListener → FCM 발송
```

### WebSocket STOMP 흐름
```java
// 시니어가 /app/location/update로 위치 전송
@MessageMapping("/location/update")
@SendToUser("/queue/location/ack")  // 발신자에게 ACK
public LocationUpdateResponse updateLocation(@Payload LocationUpdateRequest request) {
    // 서비스가 /topic/location/{seniorId}로 브로드캐스트
    return service.updateAndBroadcast(request);
}
```

## Configuration Profiles

```yaml
spring:
  profiles:
    group:
      local: "local, datasource, fcm, pay, s3"
      dev:   "dev, datasource, fcm, pay, s3"
      test:  "test, fcm, pay, s3"
```

| YAML 파일 | 내용 |
|-----------|------|
| `application-datasource.yml` | DB (MySQL/H2) |
| `application-security.yml` | JWT 시크릿·만료 시간 |
| `application-oauth.yml` | Apple·Naver·Kakao 설정 |
| `application-fcm.yml` | Firebase 서비스 계정 |
| `application-pay.yml` | TossPayments |
| `application-redis.yml` | Redis 연결 |
| `application-s3.yml` | AWS S3 자격증명·버킷 |
| `application-coolsms.yml` | SMS 인증 |
| `application-video.yml` | FFmpeg 경로·멀티파트 제한 |
| `application-medicine.yml` | 약품 API |
| `application-actuator.yml` | Actuator 엔드포인트 |

## Key Business Logic

### 인증 플로우

**로컬 회원가입 (SMS 인증)**
1. `POST /api/auth/guardian/sms/send` — SMS 발송
2. `POST /api/auth/guardian/sms/verify` → Temporary Token 발급
3. `POST /api/auth/guardian/signup` (Temporary Token 헤더) → JWT 발급
4. Temporary Token TTL: 30분 (`TemporaryMember.ttl = 1800`)

**OAuth 플로우**
1. 소셜 로그인 → 기존 유저: JWT 즉시 발급 / 신규 유저: Social Temporary Token
2. 신규 유저는 전화번호 제공 후 가입 완료
3. Apple: 개인정보 정책상 전화번호 별도 수집

**토큰 관리**
- Access Token: 단기, `Authorization: Bearer` 헤더
- Refresh Token: Redis TTL 저장, Access Token 재발급용
- Temporary Token: 회원가입 플로우 1회용, Redis `TemporaryMember`로 저장

### 회원 역할
- **시니어(Senior)**: 앨범 생성, 포인트 관리, 7자리 초대코드로 보호자 초대 (UI에서는 "부모"로 표현)
- **보호자(Guardian)**: 앨범 조회·상호작용, 포인트로 프리미엄 콘텐츠 잠금해제
- **FamilyConnection**: 보호자-`SeniorProfile` 연결 — `@ValidateFamilyAccess`로 서버 검증

### 앨범 시스템

**비동기 업로드 파이프라인**
1. 사진 → S3 즉시 업로드 (sync)
2. 영상 → 임시 `File`로 변환 (MultipartFile은 요청 종료 후 삭제됨)
3. 앨범 DB 저장 (`Status.PROCESSING`)
4. API `202 Accepted` + `albumId` 반환
5. `AlbumVideoProcessingService.processVideosAsync()`: FFmpeg → S3 → DB `ACTIVE` 업데이트 → FCM
6. 실패 시: `Status.DELETED`

**Status enum**: `ACTIVE`, `INACTIVE`, `DELETED`, `PROCESSING`
- Feed 쿼리는 `status = ACTIVE`만 조회 → `PROCESSING` 앨범 자동 제외
- MySQL ENUM에 값 추가 시 수동 ALTER 필요 (`ddl-auto: update` 미지원)

**소셜 기능**
- 2단계 댓글 (댓글 + 답글)
- 좋아요/취소
- 조회수 (유저당 최초 1회)
- 포인트 잠금해제: 50포인트, `AlbumUnlock` 엔티티, 잠금해제 후 영구 접근

### 복약 스케줄
- 알람 시간 ±30분 이내에만 인증 제출 가능
- 같은 날 동일 스케줄 중복 제출 불가
- 월별 복약 준수율 통계 제공

### 포인트 경제
- 시니어 가입 시 100포인트 지급
- 앨범 잠금해제: 50포인트 차감
- 문화비 지원 혜택 연동

### 실시간 위치 추적
1. 시니어가 JWT 인증으로 WebSocket 연결
2. `/app/location/update`로 GPS 좌표 전송
3. 서버가 family connection 검증 후 보호자에게 브로드캐스트
4. 보호자는 `/topic/location/{seniorId}` 구독
5. 위치 이력은 Redis 저장 (configurable retention)

### 심박수 AI 이상치 감지 (`heart` 도메인)
1. 웨어러블에서 심박수 15개 값 수집
2. AI 서비스로 전송: `POST http://<ai-server>:5000` — JSON 배열 15개 값
3. 응답: `0` (정상) / `1` (이상)
4. 이상 감지 시 보호자에게 FCM 알림 + `HeartRateEmergency` DB 기록
- REST(`HeartRateController`)와 WebSocket(`HeartRateWebSocketController`) 모두 지원

**AI 서비스**: Docker `rchagnhoon/widyu-ai-ver2:latest`, port 5000, 멀티 아키텍처 이미지

## Database & Persistence

### MySQL (운영)
- `widyu-domain`에 `@Entity` 엔티티
- `BaseTimeEntity`로 `createdAt`/`updatedAt` 자동 관리
- 관계: `@OneToMany`, `@ManyToOne`, `@OneToOne`

### Redis (임시 데이터)
쿼리 캐시가 아닌 **TTL 기반 임시 저장** 용도:

| 키 | 용도 |
|----|------|
| `RefreshToken` | JWT 리프레시 토큰 |
| `VerificationCode` | SMS 인증코드 (단기 TTL) |
| `TemporaryMember` | 회원가입 임시 상태 (30분) |
| `OAuthState` | OAuth CSRF 방지 |
| `SeniorLocation` | 실시간 위치 + 이력 |

`@RedisHash` + `@TimeToLive`로 자동 만료.

### QueryDSL
- Q-클래스: `build/generated/sources/annotationProcessor/java/main`
- 엔티티 변경 후 `./gradlew compileJava`로 재생성
- 복잡한 조인·동적 조건 쿼리에 사용

### H2 (테스트)
- `application-test.yml`에 in-memory DB 설정

## External Integrations

| 서비스 | 용도 |
|--------|------|
| Firebase FCM | 푸시 알림, 서비스 계정 인증 |
| AWS S3 | 미디어 파일 저장 (버킷 정책으로 접근 제어, `PUBLIC_READ` ACL 미사용) |
| Apple OAuth | JWT 기반, 공개키 동적 조회 |
| Naver OAuth | REST API |
| Kakao OAuth | REST API |
| TossPayments | 카드·가상계좌·계좌이체·간편결제 |
| Coolsms | SMS 인증코드 (국내 서비스) |
| FFmpeg | 영상 썸네일 생성·처리 |
| 공공 약품 API | 복약 스케줄 약품 정보 조회 |
| AI Heart Rate Service | Flask ML 서비스, 심박수 이상치 감지 |

## Security Implementation

- **JWT**: Stateless (Access + Refresh), 시크릿 3개 (`application-security.yml`), Refresh는 Redis 저장
- **OAuth2**: Strategy 패턴으로 멀티 프로바이더, `OAuthState`(Redis)로 CSRF 방지
- **SMS 인증**: 로컬 회원가입 필수, Coolsms로 전화번호 검증
- **역할 기반 접근**: `MemberType` enum (SENIOR/GUARDIAN), `@ValidateFamilyAccess` AOP
- **CORS**: 크로스 오리진 요청 허용 설정
