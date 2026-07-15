# WIDYU-server 아키텍처 리뷰 및 구조 개선 계획

## 1. 문서 개요

- **문서 목적:** 아키텍처 리뷰의 확인 사실, 개선안, 미검증 가설, 결정 및 실제 리팩터링 진행 상황을 누적 관리한다.
- **문서의 관리 방식:** 사실은 근거 파일과 함께 기록하고, 제안은 `개선안 제안` 또는 `작업 예정` 상태로 관리한다. 가설은 `추가 확인 사항`에만 기록한다. 완료 항목은 삭제하지 않고 상태를 `완료`로 변경한다.
- **리뷰 기준:** Java 21, Spring Boot 3.x, JPA, QueryDSL, Redis, WebSocket 환경에서 Aggregate 경계, 불변식, 트랜잭션, 의존성 방향, 테스트 가능성을 검토한다.
- **검토 범위:** Gradle 멀티 모듈 구조, member/family/auth 및 회원·가족 접근 관련 global 코드, location/parentlocation/heart와 직접 연결된 Redis·WebSocket·이벤트 코드, goal/medicine/walk/healthschedule과 관련 포인트·위치 검증 코드, album/fcm 및 앨범 업로드·알림과 직접 연결된 S3·FFmpeg·비동기 이벤트 코드, pay 및 결제와 직접 연결된 포인트·회원·외부 PG Client 코드, home/mypage/admin 조회 조합 코드, Critical·High 이슈 관련 테스트·ADR·LLD 정합성.
- **제외 범위:** Critical·High 문제와 무관한 ADR·LLD 정독, 저장소 전체 신규 구조 이슈 재검색, 실제 리팩터링 코드 변경.
- **마지막 갱신일:** 2026-07-15
- **현재 리뷰 단계:** 1차 구조 리뷰, 전체 지정 도메인 상세 리뷰, Critical·High 이슈 중심 테스트·ADR·LLD 정합성 리뷰 완료. 최종 통합 리뷰 및 리팩터링 PR 순서 확정 예정.

## 2. 현재 아키텍처 요약

- **기술 스택:** Java 21, Spring Boot 3.3.5, JPA/Hibernate, QueryDSL, Redis, WebSocket(STOMP), MySQL, H2 테스트.
- **Gradle 멀티 모듈 구조:** `widyu-api`가 `widyu-domain`에만 의존하는 단방향 구조다. 근거: [widyu-api/build.gradle](../../backend/widyu-api/build.gradle), [widyu-domain/build.gradle](../../backend/widyu-domain/build.gradle).
- **`widyu-api` 책임:** Controller, Application Service/Facade, JPA·Redis Repository, Spring 설정, WebSocket, 외부 연동.
- **`widyu-domain` 책임:** JPA Entity, RedisHash, Enum, 공통 도메인 타입 및 오류 타입.
- **현재 의존성 방향:** `Controller -> Application Service/Facade -> Repository -> Entity`가 대체로 유지된다. API의 `@Entity`, domain의 Repository는 확인되지 않았다.
- **주요 도메인:** member/family, auth, album, goal, location, heart, pay, fcm, home/mypage/admin.
- **현재 아키텍처 판정:** **Entity 모듈과 Application 모듈을 나눈 구조**에 가장 가깝다. domain은 JPA, Redis, Spring HTTP, QueryDSL에 결합되어 있어 순수 도메인 모듈은 아니다.

## 3. 확인된 강점

1. **모듈 의존 방향이 명확하다.** `widyu-api -> widyu-domain`만 프로젝트 의존성이 있으며 domain에서 API 계층 import는 확인되지 않았다. 근거: [build.gradle](../../backend/widyu-api/build.gradle).
2. **영속성 타입과 Repository의 모듈 배치가 규칙과 일치한다.** Entity 36개 및 RedisHash 7개는 domain, JPA·Redis Repository 37개는 API에 있다. 근거: `backend/widyu-domain/src/main/java`, `backend/widyu-api/src/main/java/**/repository`.
3. **웹 계층의 직접 영속성 접근이 제한되어 있다.** 확인 범위의 Controller는 Repository 또는 domain Entity를 직접 참조하지 않고 Application Service 또는 Facade를 사용한다. 근거: `backend/widyu-api/src/main/java/**/controller`.
4. **member 포인트 경쟁 상태를 실제 통합 테스트로 검증한다.** `SeniorProfile.@Version`과 재시도 정책에 대해 적립 유실 및 과차감을 검증한다. 근거: [SeniorPointConcurrencyIntegrationTest.java](../../backend/widyu-api/src/test/java/com/widyu/member/integration/SeniorPointConcurrencyIntegrationTest.java).

## 4. 구조적 이슈 목록

| ID | 이슈 | 심각도 | 상태 | 대상 영역 | 선행 테스트 | 추천 PR |
| -- | -- | --- | -- | ----- | ------ | ----- |
| ARCH-001 | domain 모듈의 기술 의존성 노출 | High | 개선안 제안 | 멀티 모듈 | TEST-005 | domain 의존성 최소화 |
| ARCH-002 | RedisHash와 JPA Entity 혼재 | Medium | 분석 중 | domain/auth/location/heart | TEST-005 | Redis 모델 경계 정리 |
| ARCH-003 | RealtimeLocationService 책임 과다 및 동기 부수효과 결합 | High | 개선안 제안 | location/realtime/fcm | TEST-008, TEST-011 | 위치 저장·브로드캐스트 분리 |
| ARCH-004 | global의 member 역방향 의존성 | High | 작업 예정 | global/member/auth | TEST-004, TEST-005 | 가족 접근 정책 단일화 |
| ARCH-005 | 화면 유스케이스의 다도메인 Repository 조합 | Medium | 개선안 제안 | home/mypage/admin | TEST-027 | 조회 경계 정리 |
| ARCH-006 | Controller 외부 API 직접 호출 예외 | Medium | 분석 중 | auth | 검토 예정 | Naver 테스트 연동 계층 정리 |
| ARCH-007 | 아키텍처 경계 자동 검증 부재 | Low | 작업 예정 | 전체 | TEST-005 | 아키텍처 경계 테스트 |
| ARCH-008 | Refresh Token 회전 검증 누락 | Critical | 완료 | auth | TEST-001 | Refresh Token rotation 검증 수정 |
| ARCH-009 | Senior 가입 행위자 타입 검증 누락 | High | 작업 예정 | auth/family | TEST-002 | 시니어 등록 행위자 검증 |
| ARCH-010 | 탈퇴 후 FamilyMembership·leader 상태 잔존 | High | 개선안 제안 | member/family/auth | TEST-003 | 탈퇴-가족 구성원 생명주기 정책 |
| ARCH-011 | 가족 접근 정책의 AOP·Service 분산 | High | 작업 예정 | member/family/global | TEST-004 | 가족 접근 정책 단일화 |
| ARCH-012 | 가입 트랜잭션 내부 외부 지오코딩 호출 | Medium | 개선안 제안 | auth/location | TEST-006 | 시니어 가입 지오코딩 경계 |
| ARCH-013 | ParentLocation 변경의 가족 소유권 검증 누락 | Critical | 작업 예정 | parentlocation/member/family | TEST-009 | 안전구역 CUD 인가 |
| ARCH-014 | STOMP location·heart topic 구독 인가 부재 | High | 작업 예정 | global/websocket/location/heart | TEST-010 | WebSocket 구독 인가 |
| ARCH-015 | 안전구역 이탈 알림 중복 차단의 경쟁 상태 | Medium | 작업 예정 | location/realtime/fcm | TEST-011 | Redis 원자 중복 차단 |
| ARCH-016 | 심박 수집·응급기록 멱등성 부재 | High | 개선안 제안 | heart | TEST-012 | 심박 배치 멱등성 |
| ARCH-017 | AI 이상 판정과 심박 저장의 장기 트랜잭션 결합 | Medium | 개선안 제안 | heart/AI | TEST-013 | 심박 수집 트랜잭션 경계 |
| ARCH-018 | 심박 이상 FCM 문서·구현 불일치 | Medium | 확인 필요 | heart/fcm/docs | TEST-012 | 심박 이상 알림 정책 결정 |
| ARCH-019 | 건강관리 포인트 지급 경로 불일치 | High | 개선안 제안 | healthschedule/walk/medicine/member | TEST-014, TEST-017 | 건강관리 보상 정책 정리 |
| ARCH-020 | 복약 포인트 정산 멱등성 부재 | High | 개선안 제안 | medicineschedule/member | TEST-016 | 복약 정산 원장 도입 |
| ARCH-021 | 복약 인증 중복 방지와 파일 업로드 트랜잭션 결합 | High | 개선안 제안 | medicineschedule/S3 | TEST-015 | 복약 인증 멱등성 |
| ARCH-022 | goal 패키지의 도메인·화면 유스케이스 혼재 | Medium | 분석 중 | goal/home/medicineschedule/walk/healthschedule | TEST-018 | 건강관리 조회 경계 정리 |
| ARCH-023 | Album 상호작용 컬렉션과 카운터 일관성 위험 | High | 개선안 제안 | album | TEST-019 | 앨범 상호작용 멱등성 |
| ARCH-024 | Album 접근 정책의 가족 범위 확인 필요 | High | 확인 필요 | album/member/family | TEST-020 | 앨범 접근 정책 고정 |
| ARCH-025 | 영상 비동기 처리 실패·재시도·S3 정리 모델 부재 | Medium | 개선안 제안 | album/S3/FFmpeg | TEST-021 | 영상 처리 실패 모델링 |
| ARCH-026 | Album 이벤트의 FCM 소유 및 동기 알림 결합 | High | 개선안 제안 | album/fcm/event | TEST-022 | 앨범 알림 후속 처리 분리 |
| ARCH-027 | FCM 토큰 소유자 변경 처리 불완전 | Medium | 개선안 제안 | fcm/member | TEST-023 | FCM 토큰 생명주기 정리 |
| ARCH-028 | 결제 승인·취소의 PG 호출과 DB 트랜잭션 결합 | High | 개선안 제안 | pay/member/PG | TEST-024 | 결제 외부 호출 경계 정리 |
| ARCH-029 | 결제 승인 멱등성의 외부 중복 호출 위험 | High | 개선안 제안 | pay/PG | TEST-025 | 결제 승인 멱등성 보강 |
| ARCH-030 | 부분 취소 멱등성·동시성 모델 부재 | High | 개선안 제안 | pay/member | TEST-026 | 결제 취소 멱등성 보강 |
| ARCH-031 | PaymentClient의 config 패키지 배치 | Low | 작업 예정 | pay/infrastructure | TEST-005 | PG Client 패키지 경계 정리 |
| ARCH-032 | mypage 조회·명령·외부 연동 책임 혼재 | High | 개선안 제안 | mypage/member/location/S3/auth | TEST-028 | 마이페이지 유스케이스 분리 |
| ARCH-033 | admin 운영 조회의 OLTP 부하와 Projection 부재 | Medium | 개선안 제안 | admin/member/album/pay/fcm/heart | TEST-029 | 관리자 Read Model 정리 |
| ARCH-034 | AdminAuditLog 저장 정책의 트랜잭션 일관성 차이 | Medium | 개선안 제안 | admin/audit | TEST-030 | 관리자 감사 로그 정책 정리 |

### ARCH-001 domain 모듈의 기술 의존성 노출

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-domain`
- **관련 패키지 및 파일:** [widyu-domain/build.gradle](../../backend/widyu-domain/build.gradle), `com.widyu.global.error.ErrorCode`
- **확인된 사실:** JPA, Redis, Web, QueryDSL 의존성을 `api`로 노출하며 `ErrorCode`가 Spring `HttpStatus`를 참조한다.
- **문제:** domain 소비자가 영속성·Redis·HTTP 기술 의존성을 함께 갖는다.
- **왜 문제인지:** 도메인 규칙과 HTTP 오류, 저장 기술의 변경이 같은 모듈 변경으로 결합된다.
- **깨질 수 있는 동작 또는 불변식:** 직접적인 기능 불변식보다 모듈 독립성·테스트 격리가 약화된다.
- **목표 구조:** 단기적으로 영속성 모델 모듈임을 명확히 하고 불필요한 Web 노출을 제거한다.
- **개선 방향:** `starter-web` 제거 가능성부터 검증하고, 오류 모델과 순수 타입 분리를 장기 검토한다.
- **선행 테스트:** TEST-005.
- **예상 영향 범위:** Gradle 의존성, 예외 처리, Entity 스캔.
- **추천 PR 단위:** `domain 의존성 최소화`.
- **관련 ADR·LLD:** 없음.
- **결정 이력:** 미결정.
- **추가 확인 사항:** QueryDSL을 domain의 공개 API로 유지해야 하는지 확인 필요.

### ARCH-002 RedisHash와 JPA Entity 혼재

- **상태:** 분석 중
- **심각도:** Medium
- **관련 모듈:** `widyu-domain`, `widyu-api`
- **관련 패키지 및 파일:** `auth/RefreshToken`, `auth/TemporaryMember`, `location/SeniorLocation`, `heart/HeartRateResult`
- **확인된 사실:** TTL 기반 RedisHash와 JPA Entity가 동일 domain 모듈에 배치되고 Repository는 API에 있다.
- **문제:** Aggregate와 인증·캐시 저장 모델의 경계가 패키지 수준에서 드러나지 않는다.
- **왜 문제인지:** TTL, Redis 직렬화, 키 설계가 도메인 모델처럼 취급될 수 있다.
- **깨질 수 있는 동작 또는 불변식:** Redis 모델 이동 시 TTL·직렬화·Repository 계약 회귀 위험.
- **목표 구조:** Redis 저장 모델을 명확한 하위 패키지 또는 Adapter 경계로 구분한다.
- **개선 방향:** 단기적으로 패키지 분류, 장기적으로 Redis Adapter 분리를 검토한다.
- **선행 테스트:** TEST-005 및 Redis 역직렬화 테스트.
- **예상 영향 범위:** auth, location, heart Redis Repository.
- **추천 PR 단위:** `Redis 모델 경계 정리`.
- **관련 ADR·LLD:** ADR-0002.
- **결정 이력:** 미결정.
- **추가 확인 사항:** RedisHash를 도메인 모델로 유지해야 하는 업무상 이유 확인 필요.

### ARCH-003 RealtimeLocationService 책임 과다 및 동기 부수효과 결합

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `location/realtime/application/RealtimeLocationService`, `fcm/event/safezone/listener/SafeZoneNotificationListener`
- **확인된 사실:** 한 Service가 최신 위치 RedisHash, 15분 trail, 24시간 stay, 안전구역 Repository 조회, 이벤트 발행, WebSocket 브로드캐스트를 직접 조합한다. `SafeZoneExitEvent`는 기본 동기 `@EventListener`에서 FCM을 호출한다.
- **문제:** 위치 핫패스에 저장·안전구역 판정·FCM·WebSocket 전달이 결합된다.
- **왜 문제인지:** FCM 또는 WebSocket 예외가 위치 요청을 실패시킬 수 있고, 이미 반영된 Redis 상태는 관계형 트랜잭션 롤백과 원자적으로 묶이지 않는다.
- **깨질 수 있는 동작 또는 불변식:** 최신 위치·trail·stay의 일관성, 위치 갱신 성공과 보호자 전달의 분리, 안전구역 이탈 알림 순서.
- **목표 구조:** 위치 상태 갱신은 짧은 유스케이스로 유지하고, 전달·알림은 원자적 중복 차단 뒤 커밋 이후 후속 처리로 분리한다.
- **개선 방향:** `LocationStateUpdater`, 안전구역 판정, 전달 publisher의 책임을 분리하되 durable outbox 도입은 현재 규모에서 보류한다.
- **선행 테스트:** TEST-008, TEST-011.
- **예상 영향 범위:** location, healthschedule, fcm.
- **추천 PR 단위:** `위치 저장·브로드캐스트 분리`.
- **관련 ADR·LLD:** [ADR-0007](../adr/ADR-0007-location-event-visit-verification.md), [LLD-0001](../lld/LLD-0001-websocket-realtime-location.md), [LLD-0009](../lld/LLD-0009-location-based-health-schedule-verification.md).
- **결정 이력:** 위치 상태 모델의 분리는 유지하고, 외부 전달의 정확한 실패·재시도 정책은 미결정이다.
- **추가 확인 사항:** 다중 인스턴스 운영 여부와 알림 전달 보장 수준 확인 필요.

### ARCH-013 ParentLocation 변경의 가족 소유권 검증 누락

- **상태:** 작업 예정
- **심각도:** Critical
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `location/parentlocation/application/ParentLocationService`, `location/parentlocation/controller/ParentLocationController`
- **확인된 사실:** 생성·수정·삭제는 요청 `memberId`로 Member를 조회할 뿐 현재 사용자, 대상 시니어 유형, FamilyMembership을 검증하지 않는다.
- **문제:** 연결되지 않은 사용자가 다른 회원의 안전구역을 변경할 수 있다.
- **왜 문제인지:** 안전구역은 위치 추적과 이탈 알림의 입력이며, 변경 권한이 가족 관계 불변식을 직접 침해한다.
- **깨질 수 있는 동작 또는 불변식:** 연결된 보호자만 해당 시니어의 안전구역을 관리한다.
- **목표 구조:** parentlocation CUD가 member/family Application 정책을 통해 대상 시니어 소유권을 확인한다.
- **개선 방향:** `FamilyAccessService` 또는 동등한 정책 API의 `assertGuardianCanAccessSenior`를 CUD에 적용하고 guardian Member 대상 생성을 거부한다.
- **선행 테스트:** TEST-009.
- **예상 영향 범위:** parentlocation REST API, member/family 접근 정책.
- **추천 PR 단위:** `fix(location): authorize parent location mutations`.
- **관련 ADR·LLD:** ADR-0002, LLD-0001.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 시니어 본인에게 안전구역 CUD 권한을 부여할지 제품 정책 확인 필요.

### ARCH-014 STOMP location·heart topic 구독 인가 부재

- **상태:** 작업 예정
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `global/websocket/JwtChannelInterceptor`, `global/config/WebSocketConfig`, `location/realtime/controller`, `heart/controller/HeartRateWebSocketController`
- **확인된 사실:** STOMP 인터셉터는 `CONNECT` 인증만 수행하고 `SUBSCRIBE` destination의 memberId와 가족 관계를 확인하지 않는다. simple broker가 `/topic`, `/queue`를 처리한다.
- **문제:** 인증된 회원이 임의 시니어의 위치 또는 심박 topic을 구독할 가능성이 있다.
- **왜 문제인지:** REST 가족 접근 AOP가 WebSocket 구독 경로에는 적용되지 않는다.
- **깨질 수 있는 동작 또는 불변식:** 위치·심박수는 본인 또는 연결된 보호자만 조회한다.
- **목표 구조:** WebSocket transport adapter가 구독 destination을 인가하고 가족 정책은 member/family 경계에서 재사용한다.
- **개선 방향:** `SUBSCRIBE` 시 `/topic/location/senior/{memberId}`, `/topic/heart-rate/{memberId}`를 검사해 가족 접근 정책을 호출한다.
- **선행 테스트:** TEST-010.
- **예상 영향 범위:** WebSocket 인증, location·heart 클라이언트 구독.
- **추천 PR 단위:** `fix(websocket): authorize family-scoped subscriptions`.
- **관련 ADR·LLD:** ADR-0002, LLD-0001.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 운영 중 클라이언트가 실제로 구독하는 destination 목록 확인 필요.

### ARCH-015 안전구역 이탈 알림 중복 차단의 경쟁 상태

- **상태:** 작업 예정
- **심각도:** Medium
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `location/realtime/application/RealtimeLocationService`
- **확인된 사실:** 30분 중복 방지는 `hasKey()` 다음 `set()`으로 구현되어 원자적이지 않다.
- **문제:** 동시 위치 요청이 모두 키 없음으로 판단하면 복수 이벤트와 FCM을 발행할 수 있다.
- **왜 문제인지:** 안전구역 이탈은 중복 알림이 사용자 신뢰를 직접 훼손하는 외부 부수효과다.
- **깨질 수 있는 동작 또는 불변식:** 동일 이탈 상태에서 30분 내 알림은 한 번만 보낸다.
- **목표 구조:** Redis의 원자적 점유로 이벤트 발행 권한을 한 요청에만 부여한다.
- **개선 방향:** `setIfAbsent(..., TTL)` 성공 시에만 이벤트를 발행한다.
- **선행 테스트:** TEST-011.
- **예상 영향 범위:** location, fcm.
- **추천 PR 단위:** `fix(location): atomically deduplicate safe-zone alerts`.
- **관련 ADR·LLD:** LLD-0001.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 재진입 직후 재이탈 알림의 제품 정책 확인 필요.

### ARCH-016 심박 수집·응급기록 멱등성 부재

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `heart/application/HeartRateService`, `heart/HeartRateEvent`, `heart/HeartRateEmergency`
- **확인된 사실:** 이상 배치마다 Event 15건과 Emergency 1건을 무조건 저장하며, 배치 식별자·중복 조회·DB 유니크 제약이 없다. WebSocket 전송은 저장 트랜잭션 종료 뒤에 수행된다.
- **문제:** ACK 또는 브로드캐스트 실패 후 재전송하면 동일 측정 이력과 응급기록이 중복될 수 있다.
- **왜 문제인지:** `HeartRateEmergency`는 Event와 FK로 연결되지 않아 원천 측정 배치를 판별할 수 없다.
- **깨질 수 있는 동작 또는 불변식:** 하나의 측정 배치당 응급상황은 한 번만 기록·알림한다.
- **목표 구조:** 심박 수집에 멱등 키를 두고 Event·Emergency가 동일 원천 배치를 추적한다.
- **개선 방향:** 클라이언트 배치 ID 또는 측정 구간 기반 키와 DB 유니크 제약을 설계한다. Event와 Emergency를 하나의 객체 컬렉션으로 병합하지는 않는다.
- **선행 테스트:** TEST-012.
- **예상 영향 범위:** heart DTO, JPA 스키마, WebSocket 재시도.
- **추천 PR 단위:** `refactor(heart): make heart-rate ingestion idempotent`.
- **관련 ADR·LLD:** LLD-0002 확인 필요.
- **결정 이력:** Event와 Emergency는 현재 코드상 별도 Aggregate로 유지한다.
- **추가 확인 사항:** 장치가 제공하는 안정적인 측정 배치 식별자 유무 확인 필요.

### ARCH-017 AI 이상 판정과 심박 저장의 장기 트랜잭션 결합

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `heart/application/HeartRateService`, `heart/application/HeartRateAnomalyDetector`
- **확인된 사실:** `@Transactional` 심박 처리 안에서 외부 AI HTTP 호출 뒤 Redis 최신 결과, JPA Event, Emergency를 함께 저장한다.
- **문제:** AI 지연·장애가 심박 기록 전체를 막고, 배치 수준 판정 결과가 모든 Event 상태에 중복 저장된다.
- **왜 문제인지:** 외부 네트워크 대기와 저장 트랜잭션이 결합되고 판정 모델·원천 응답을 추적할 수 없다.
- **깨질 수 있는 동작 또는 불변식:** AI 성공 시 15개 이력·최신 상태·응급기록의 일관성, AI 실패 시 기록 정책.
- **목표 구조:** AI 호출은 저장 트랜잭션 밖에서 수행하고, 판정 결과를 명시적 입력으로 짧은 저장 트랜잭션에 전달한다.
- **개선 방향:** 먼저 실패 시 저장하지 않는 현재 정책을 Characterization Test로 고정한다. 판정 메타데이터 영속화는 필요성 확인 후 별도 설계한다.
- **선행 테스트:** TEST-013.
- **예상 영향 범위:** heart, AI 외부 연동.
- **추천 PR 단위:** `refactor(heart): isolate anomaly detection transaction boundary`.
- **관련 ADR·LLD:** 문서 없음.
- **결정 이력:** 미결정.
- **추가 확인 사항:** AI 미가용 시 원본 심박 기록을 보존해야 하는지 제품 정책 확인 필요.

### ARCH-018 심박 이상 FCM 문서·구현 불일치

- **상태:** 확인 필요
- **심각도:** Medium
- **관련 모듈:** `widyu-api`, `docs`
- **관련 패키지 및 파일:** `heart/application/HeartRateService`, `heart/application/HeartMessageService`, `docs/lld/LLD-0002-fcm-notification.md`
- **확인된 사실:** LLD-0002는 심박수 이상 감지 알림을 목록에 두지만, 확인한 `HEART_MESSAGE` FCM은 수동 가족 메시지에서만 호출된다.
- **문제:** 심박 이상 FCM이 실제 요구사항인지와 중복 알림 방지 기준을 구현·문서 어느 쪽에서도 확정할 수 없다.
- **왜 문제인지:** 응급기록 중복 방지 설계와 보호자 알림 정책이 함께 결정되어야 한다.
- **깨질 수 있는 동작 또는 불변식:** 이상 감지 시 보호자 알림 여부와 같은 배치의 중복 알림 방지.
- **목표 구조:** 심박 이상 알림의 필요 여부, 수신자, 멱등 키, 실패 정책을 ADR/LLD로 명시한다.
- **개선 방향:** 제품 결정 후 LLD를 구현에 맞게 수정하거나 별도 HeartRateAnomaly 이벤트·리스너를 설계한다.
- **선행 테스트:** TEST-012.
- **예상 영향 범위:** heart, fcm, 문서.
- **추천 PR 단위:** `docs(lld): decide heart anomaly notification policy`.
- **관련 ADR·LLD:** LLD-0002.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 실제 알림 요구사항과 기존 앱 클라이언트 기대 동작 확인 필요.

### ARCH-019 건강관리 포인트 지급 경로 불일치

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `goal/healthschedule/application/HealthScheduleRewardService`, `goal/walk/application/WalkService`, `goal/medicineschedule/scheduler/MedicineScheduleRewardScheduler`, `member/application/SeniorProfileService`, `member/SeniorProfile`, `member/PointHistory`
- **확인된 사실:** 건강 일정 보상 서비스는 `HealthSchedule.claimReward()`로 `isReward`만 변경하고 실제 포인트·원장 기록은 저장하지 않는다. 걷기 목표 달성은 `WalkService.updateSteps()`에서 `SeniorProfile.addPoints()`를 직접 호출하며 `PointHistory`를 남기지 않는다. 복약 정산만 `SeniorProfileService.addPointsToMember()`를 호출한다.
- **문제:** 건강관리 보상 경로마다 포인트 적립 방식, 원장 기록, 중복 지급 방지 기준이 다르다.
- **왜 문제인지:** 포인트는 금전성 상태에 가까운데 일부 경로가 중앙 포인트 서비스와 원장 기록을 우회하거나 아직 구현되지 않은 상태다.
- **깨질 수 있는 동작 또는 불변식:** 보상 지급 시 포인트 잔액과 PointHistory가 함께 증가한다. 완료되지 않은 일정이나 이미 보상 받은 일정은 다시 보상되지 않는다.
- **목표 구조:** 건강관리 보상은 동일한 포인트 지급 정책 API를 사용하고, 각 기능은 보상 가능 여부와 멱등 키만 소유한다.
- **개선 방향:** 먼저 건강 일정·걷기·복약의 보상 정책을 비교하는 Characterization Test를 작성한다. 이후 `SeniorProfileService` 또는 별도 보상 Application Service를 통해 포인트·원장 기록을 일관되게 남긴다.
- **선행 테스트:** TEST-014, TEST-017.
- **예상 영향 범위:** healthschedule, walk, medicineschedule, member point.
- **추천 PR 단위:** `refactor(goal): centralize health reward point policy`.
- **관련 ADR·LLD:** [LLD-0003](../lld/LLD-0003-payment-points.md), [LLD-0009](../lld/LLD-0009-location-based-health-schedule-verification.md).
- **결정 이력:** 미결정.
- **추가 확인 사항:** 건강 일정 실제 보상 지급이 제품 요구사항인지, 걷기 보상에 PointHistory가 필요한지 확인 필요.

### ARCH-020 복약 포인트 정산 멱등성 부재

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `goal/medicineschedule/scheduler/MedicineScheduleRewardScheduler`, `goal/medicineschedule/repository/MedicationProofRepository`, `goal/medicineschedule/repository/MedicineScheduleRepository`
- **확인된 사실:** 매일 자정 Scheduler가 전날 `MedicationProof`가 있는 회원을 조회하고 인증 횟수와 유효 스케줄 수로 포인트를 계산해 `SeniorProfileService.addPointsToMember()`를 호출한다. 정산 완료 여부를 저장하는 모델, 날짜·회원 단위 unique 제약, 재실행 멱등 가드는 확인되지 않았다.
- **문제:** Scheduler가 같은 날짜를 재실행하거나 다중 인스턴스에서 중복 실행되면 같은 인증 기록으로 포인트가 반복 적립될 수 있다.
- **왜 문제인지:** 복약 인증은 수행 기록이고 포인트 정산은 후속 보상이다. 후속 보상은 재시도 가능해야 하므로 별도 정산 식별자가 필요하다.
- **깨질 수 있는 동작 또는 불변식:** 회원·날짜 기준 복약 보상은 한 번만 정산된다.
- **목표 구조:** 복약 정산에 `memberId + targetDate` 기준 멱등 기록을 두고 성공한 정산만 포인트 지급 완료로 본다.
- **개선 방향:** 정산 원장 또는 `PointHistory` description만으로 충분한지 검토한다. 권장안은 별도 `MedicationRewardSettlement` 또는 범용 reward ledger를 두고 DB unique 제약으로 중복 정산을 차단하는 것이다.
- **선행 테스트:** TEST-016.
- **예상 영향 범위:** medicineschedule scheduler, member point, 운영 배치.
- **추천 PR 단위:** `fix(medicine): make medication reward settlement idempotent`.
- **관련 ADR·LLD:** [LLD-0008](../lld/LLD-0008-medicine-schedule-versioning.md), [LLD-0003](../lld/LLD-0003-payment-points.md).
- **결정 이력:** 미결정.
- **추가 확인 사항:** 운영 환경의 Scheduler 단일 실행 보장 여부 확인 필요.

### ARCH-021 복약 인증 중복 방지와 파일 업로드 트랜잭션 결합

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `goal/medicineschedule/application/MedicationProofService`, `goal/medicineschedule/repository/MedicationProofRepository`, `medicine/MedicationProof`, `global/infrastructure/s3/S3Service`
- **확인된 사실:** 복약 인증은 `existsByMedicineScheduleAndVerifiedAtBetween()` 선조회 후 S3 업로드를 수행하고 `MedicationProof`를 저장한다. `MedicationProof`에는 회원·스케줄·날짜 기준 unique 제약이 없고, S3 업로드가 `@Transactional` 메서드 안에서 실행된다.
- **문제:** 동시 인증 요청은 둘 다 중복 없음으로 판단할 수 있고, 외부 파일 업로드 시간이 DB 트랜잭션과 결합된다.
- **왜 문제인지:** 인증 기록은 포인트 정산과 알림 상태의 입력이다. 중복 인증은 일일 달성률과 정산 포인트를 왜곡하고, S3 실패·지연은 인증 트랜잭션을 길게 점유한다.
- **깨질 수 있는 동작 또는 불변식:** 회원은 같은 스케줄을 같은 날짜에 한 번만 인증한다. 파일 업로드 실패 시 DB 인증 기록이 남지 않는다.
- **목표 구조:** 복약 인증 중복은 DB unique 또는 멱등 키로 보장하고, 파일 업로드 실패 정책은 Characterization Test로 먼저 고정한다.
- **개선 방향:** `MedicationProof`에 인증일 컬럼 또는 멱등 키를 도입해 `(member_id, medicine_schedule_id, verified_date)` unique 제약을 둔다. S3 업로드는 저장 트랜잭션 전 사전 업로드와 실패 보상 삭제 정책을 명시한다.
- **선행 테스트:** TEST-015.
- **예상 영향 범위:** medicineschedule verification API, S3 파일 정리, monthly/home 통계.
- **추천 PR 단위:** `fix(medicine): enforce medication proof idempotency`.
- **관련 ADR·LLD:** [LLD-0007](../lld/LLD-0007-medicine-daily-status.md), [LLD-0008](../lld/LLD-0008-medicine-schedule-versioning.md).
- **결정 이력:** 미결정.
- **추가 확인 사항:** 이미지 없는 인증을 허용하는 현재 정책을 유지할지 확인 필요.

### ARCH-022 goal 패키지의 도메인·화면 유스케이스 혼재

- **상태:** 분석 중
- **심각도:** Medium
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `goal/medicineschedule`, `goal/walk`, `goal/healthschedule`, `goal/home`, `medicine`, `walk`, `healthschedule`, `goal/DailyGoalStatus`
- **확인된 사실:** API는 `goal/medicineschedule`, `goal/walk`, `goal/healthschedule`, `goal/home`으로 건강관리 기능을 묶지만 domain은 `medicine`, `walk`, `healthschedule`, `goal` enum으로 나뉜다. `GoalHomeService`는 약·걷기·건강일정 Repository를 조합해 화면 지표를 계산한다.
- **문제:** `goal`이 도메인 Aggregate 이름인지 화면 탭/카테고리 이름인지 코드 구조만으로 명확하지 않다.
- **왜 문제인지:** 상태 전이와 보상 정책은 하위 기능별로 다르고, 홈 조회는 Aggregate가 아니라 Read Model에 가깝다. 같은 `goal` prefix가 쓰기 유스케이스와 화면 조회를 함께 담으면 변경 이유가 섞인다.
- **깨질 수 있는 동작 또는 불변식:** 직접적인 단일 불변식보다 조회 통계, API 명칭, 도메인 책임 변경의 영향 범위가 불명확해진다.
- **목표 구조:** 단기적으로 현재 API 경로는 유지하되 `goal/home`은 건강관리 Read Model, `medicine/walk/healthschedule`은 하위 기능 유스케이스로 구분한다.
- **개선 방향:** 패키지 이동보다 먼저 `GoalHomeService`의 조회 조합 테스트를 보강한다. 이후 `goal`을 화면 컨텍스트로 유지할지, `health` 상위 컨텍스트로 재명명할지 ADR 후보로 검토한다.
- **선행 테스트:** TEST-018.
- **예상 영향 범위:** goal home, medicineschedule, walk, healthschedule API 패키지.
- **추천 PR 단위:** `refactor(goal): clarify health dashboard query boundary`.
- **관련 ADR·LLD:** [LLD-0007](../lld/LLD-0007-medicine-daily-status.md), [LLD-0008](../lld/LLD-0008-medicine-schedule-versioning.md).
- **결정 이력:** 미결정.
- **추가 확인 사항:** 클라이언트 API 경로 변경 가능성은 현재 확인하지 않았으므로 변경 전 제품·앱 영향 확인 필요.

### ARCH-023 Album 상호작용 컬렉션과 카운터 일관성 위험

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `album/Album`, `album/AlbumLike`, `album/AlbumView`, `album/AlbumUnlock`, `album/application/AlbumLikeService`, `album/application/AlbumViewService`, `album/application/AlbumUnlockService`
- **확인된 사실:** `Album`은 `comments`, `likes`, `views`를 `cascade = ALL`, `orphanRemoval = true` 컬렉션으로 보유하고 동시에 `likeCount`, `commentCount`, `viewCount` 카운터를 가진다. Like/View/Unlock은 각각 DB unique 제약이 있지만 Service는 `exists` 선조회 후 저장하고, 카운터는 Service가 직접 증가·감소시킨다. `AlbumService.getAlbumDetail()`의 read-only 트랜잭션 안에서 `AlbumViewService.recordView()`가 `REQUIRES_NEW`로 호출되며 전달받은 `Album` 인스턴스의 `viewCount`를 증가시킨다.
- **문제:** 대량 증가하는 상호작용 기록과 카운터 일관성이 Aggregate 내부 컬렉션, DB unique 제약, Service 조건문에 분산되어 있다.
- **왜 문제인지:** 동시 좋아요·조회·잠금 해제 요청에서 unique 제약은 중복 row를 막을 수 있지만, 카운터 증가와 포인트 차감·이벤트 발행의 정확성은 별도 검증이 필요하다. 특히 다른 트랜잭션으로 전달된 `Album` 엔티티의 `viewCount` 변경이 실제 저장되는지는 통합 테스트로 확인되어야 한다.
- **깨질 수 있는 동작 또는 불변식:** 한 회원은 같은 앨범에 한 번만 좋아요·조회·잠금 해제한다. 앨범 카운터는 실제 Like/View/Comment row 수와 의미상 일치한다. 잠금 해제 중복 요청은 포인트를 중복 차감하지 않는다.
- **목표 구조:** `Album`은 콘텐츠·미디어·처리 상태를 중심으로 두고, Like/View/Unlock은 독립 상호작용 기록으로 관리한다. 카운터는 DB 제약 또는 명시적 집계 정책과 함께 검증한다.
- **개선 방향:** 먼저 JPA 통합·동시성 테스트로 현재 카운터와 unique 동작을 고정한다. 이후 대량 컬렉션을 `Album` Root 컬렉션으로 유지할 필요가 없다면 cascade 컬렉션 의존을 낮추고, 카운터 갱신은 단일 책임 서비스나 DB update 쿼리로 정리한다.
- **선행 테스트:** TEST-019.
- **예상 영향 범위:** album 상세 조회, 좋아요·조회·댓글·잠금 해제 API, 포인트 차감.
- **추천 PR 단위:** `test(album): characterize interaction idempotency and counters`.
- **관련 ADR·LLD:** ADR-0005, LLD-0003.
- **결정 이력:** 미결정.
- **추가 확인 사항:** `Album.views.viewCount`를 누적 조회 수로 사용할 계획인지, 현재처럼 최초 조회 기록만 의미하는지 제품 요구 확인 필요.

### ARCH-024 Album 접근 정책의 가족 범위 확인 필요

- **상태:** 확인 필요
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `album/application/AlbumPermissionService`, `album/application/AlbumFeedService`, `album/application/AlbumUnlockService`, `album/repository/AlbumRepository`
- **확인된 사실:** `AlbumPermissionService`는 작성자에게 허용하고, guardian 회원에게는 추가 가족 관계 확인 없이 ACTIVE 앨범 접근을 허용한다. senior 회원은 `AlbumUnlock`이 있으면 접근할 수 있다. `AlbumFeedService`는 ACTIVE 앨범을 전역 피드처럼 조회한다.
- **문제:** 앨범이 가족 공유 콘텐츠인지, 전체 보호자·시니어가 소비하는 공개/프리미엄 콘텐츠인지 정책이 코드 경계만으로 명확하지 않다.
- **왜 문제인지:** 가족 공유 모델이라면 guardian 전체 접근과 senior의 임의 앨범 잠금 해제는 개인정보 접근 불변식을 깨뜨린다. 반대로 전역 피드가 의도라면 이 정책을 명시한 ADR/LLD와 API 계약이 필요하다.
- **깨질 수 있는 동작 또는 불변식:** 앨범은 작성자 또는 연결된 가족만 조회한다. 또는 전역 피드 앨범은 명시적으로 공개 범위가 부여된 콘텐츠만 조회한다.
- **목표 구조:** 앨범 접근 범위를 제품 정책으로 확정하고, 가족 범위 접근이면 member/family 접근 정책을 Application Service에서 재사용한다. 전역 피드가 맞다면 `public feed` 정책을 코드와 문서에 명시한다.
- **개선 방향:** `AlbumPermissionService`와 `AlbumFeedService`의 현재 동작을 Characterization Test로 고정한 뒤, 가족 범위 필터가 필요한지 결정한다.
- **선행 테스트:** TEST-020.
- **예상 영향 범위:** 앨범 피드, 상세 조회, 잠금 해제, FCM 수신자 산정.
- **추천 PR 단위:** `test(album): characterize album visibility policy`.
- **관련 ADR·LLD:** ADR-0005 확인 필요.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 앨범 피드가 가족 전용인지 전역 콘텐츠 피드인지 제품 정책 확인 필요.

### ARCH-025 영상 비동기 처리 실패·재시도·S3 정리 모델 부재

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `album/Album`, `album/application/AlbumFacadeImpl`, `album/application/AlbumFileService`, `album/application/AlbumVideoProcessingService`, `global/infrastructure/s3/S3ServiceImpl`
- **확인된 사실:** 영상이 포함된 앨범은 `PROCESSING` 상태로 저장되고 `@Async @Transactional` 영상 처리 후 `completeVideoProcessing()`으로 ACTIVE가 된다. 실패 시 catch 블록에서 앨범을 `DELETED`로 표시한다. 영상 처리 작업 ID, 실패 사유, 재시도 횟수, 재개 가능한 작업 저장 모델은 없다. 일부 영상·썸네일 업로드 후 후속 단계가 실패하면 이미 업로드된 S3 객체 정리 보장은 명확하지 않다.
- **문제:** 비동기 영상 처리 실패가 앨범 상태만으로 축약되고, 재시도·운영 관측·부분 업로드 정리 기준이 모델링되지 않았다.
- **왜 문제인지:** 서버 재시작이나 FFmpeg/S3 일시 장애가 발생하면 사용자는 처리 실패 원인을 알 수 없고 운영자는 재처리 대상을 안정적으로 찾기 어렵다. S3 부분 성공은 고아 객체와 비용 누수로 이어질 수 있다.
- **깨질 수 있는 동작 또는 불변식:** PROCESSING 앨범은 최종적으로 ACTIVE 또는 명시적 실패 상태가 된다. 실패한 비동기 작업은 중복 없이 재시도하거나 안전하게 포기된다. 실패 시 불필요한 S3 객체가 남지 않는다.
- **목표 구조:** 현재 규모에서는 durable queue 도입을 즉시 확정하지 않되, 실패 상태·재처리 가능성·S3 보상 삭제 정책을 명시한다.
- **개선 방향:** 먼저 LLD-0006의 현재 보류 리스크를 테스트로 고정한다. 이후 `VIDEO_FAILED` 같은 상태 또는 별도 processing job/read model 도입 여부를 결정하고, 영상 업로드 단위의 보상 삭제를 정리한다.
- **선행 테스트:** TEST-021.
- **예상 영향 범위:** 앨범 업로드 API, 영상 처리 비동기 작업, S3 파일 관리.
- **추천 PR 단위:** `refactor(album): model video processing failure policy`.
- **관련 ADR·LLD:** ADR-0004, LLD-0006.
- **결정 이력:** LLD-0006은 큐·재시도·재시작 복구를 현재 범위 밖으로 둔다.
- **추가 확인 사항:** 영상 처리 실패 앨범을 사용자에게 노출할지, 자동 재시도 횟수를 둘지 확인 필요.

### ARCH-026 Album 이벤트의 FCM 소유 및 동기 알림 결합

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `fcm/event/album/dto`, `fcm/event/album/listener/AlbumNotificationListener`, `album/application/AlbumService`, `album/application/AlbumLikeService`, `album/application/AlbumCommentService`, `album/application/AlbumUnlockService`, `fcm/application/FcmService`
- **확인된 사실:** Album Service들이 `com.widyu.fcm.event.album.dto`의 이벤트 DTO를 직접 발행한다. `AlbumNotificationListener`는 기본 동기 `@EventListener`이며 `FcmService`를 호출한다. `FcmService`는 `@Transactional` 안에서 외부 FCM HTTP 호출과 `FcmNotification` 저장을 함께 수행하고, RestTemplate 런타임 예외 전파 가능성이 LLD-0002에도 리스크로 기록되어 있다.
- **문제:** 앨범 도메인 이벤트의 계약 소유자가 fcm 패키지에 있고, FCM 전송 실패가 앨범 쓰기 트랜잭션에 영향을 줄 수 있다.
- **왜 문제인지:** 좋아요·댓글·잠금 해제 같은 앨범 상태 변경은 알림 전송 실패와 독립적으로 성공해야 한다. 이벤트 타입이 FCM 소유이면 향후 다른 소비자가 생겨도 이벤트 계약을 알림 인프라 기준으로 변경하게 된다.
- **깨질 수 있는 동작 또는 불변식:** 앨범 생성·좋아요·댓글·잠금 해제의 DB 변경은 알림 실패로 롤백되지 않는다. 알림은 커밋된 앨범 상태에 대해서만 발송된다.
- **목표 구조:** 이벤트 계약은 album 또는 application event 경계에서 소유하고, FCM은 커밋 이후 후속 처리 어댑터로 소비한다.
- **개선 방향:** 우선 FCM 실패가 앨범 트랜잭션을 롤백하는지 Characterization Test로 확인한다. 이후 `@TransactionalEventListener(phase = AFTER_COMMIT)` 또는 실패 격리 wrapper를 적용하고, 이벤트 DTO 패키지 소유권을 정리한다. durable outbox는 전달 보장 수준을 확정한 뒤 검토한다.
- **선행 테스트:** TEST-022.
- **예상 영향 범위:** album Service, fcm listener, 알림 저장, 포인트 차감이 포함된 unlock 흐름.
- **추천 PR 단위:** `refactor(album): isolate fcm notification side effects`.
- **관련 ADR·LLD:** LLD-0002, LLD-0006.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 앨범 알림의 전달 보장 수준과 실패 재시도 요구 확인 필요.

### ARCH-027 FCM 토큰 소유자 변경 처리 불완전

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `fcm/MemberFcmToken`, `fcm/application/MemberFcmTokenService`, `fcm/repository/MemberFcmTokenRepository`
- **확인된 사실:** `MemberFcmToken.token`은 unique이며, `MemberFcmTokenService.saveOrActivateToken()`은 같은 토큰이 다른 회원에게 있으면 기존 토큰을 비활성화하고 반환한다. 이 분기에서는 현재 회원 소유의 활성 토큰을 새로 만들거나 소유자를 이전하지 않는다.
- **문제:** 같은 기기 토큰을 다른 회원이 로그인 후 등록하는 경우, 토큰이 비활성화된 채 현재 회원에게 연결되지 않을 수 있다.
- **왜 문제인지:** FCM 토큰은 도메인 Aggregate라기보다 알림 인프라 저장 모델이지만, 회원-토큰 활성 관계는 알림 전달의 핵심 불변식이다. 이 불변식이 깨지면 정상 회원이 알림을 받지 못한다.
- **깨질 수 있는 동작 또는 불변식:** 하나의 활성 FCM 토큰은 정확히 한 회원에게 귀속되고, 새 로그인 사용자가 등록한 토큰은 그 회원에게 활성화된다.
- **목표 구조:** 토큰 등록은 소유자 변경, 기존 토큰 비활성화, 현재 회원 활성화를 하나의 명시적 정책으로 처리한다.
- **개선 방향:** 토큰 재사용/소유자 변경 시나리오 테스트를 추가한 뒤, 기존 row를 현재 회원으로 이전할지 새 row 생성이 가능한 스키마로 바꿀지 결정한다.
- **선행 테스트:** TEST-023.
- **예상 영향 범위:** FCM 토큰 등록 API, 알림 수신, 회원 로그아웃·기기 변경.
- **추천 PR 단위:** `fix(fcm): handle token ownership transfer`.
- **관련 ADR·LLD:** LLD-0002.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 한 기기에서 다중 계정 전환을 지원하는지 앱 정책 확인 필요.

### ARCH-028 결제 승인·취소의 PG 호출과 DB 트랜잭션 결합

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `pay/application/PaymentService`, `pay/config/PaymentClient`, `member/application/SeniorProfileService`, `member/SeniorProfile`, `member/PointHistory`
- **확인된 사실:** `PaymentService.confirmPayment()`는 `@Transactional` 안에서 주문 조회·검증 후 `paymentClient.confirmPayment()`를 호출하고, 같은 트랜잭션에서 `Payment` 저장, `PaymentOrder.markPaid()`, `SeniorProfileService.addPointsToMember()`를 수행한다. `cancelPayment()`도 같은 트랜잭션 안에서 포인트 잔액 검증, `paymentClient.cancelPayment()`, `PaymentCancel` 저장, `Payment.cancel()`, `SeniorProfileService.deductPointsFromMember()`를 수행한다. 코드 주석과 LLD-0003은 포인트 낙관적 락 충돌 시 롤백 후 409를 반환하고 클라이언트 재시도를 기대한다고 설명한다.
- **문제:** 외부 PG 승인·취소 성공과 내부 DB 커밋이 하나의 원자적 트랜잭션으로 묶일 수 없는데, 현재 코드는 열린 DB 트랜잭션 안에서 외부 호출을 수행한다.
- **왜 문제인지:** PG 호출 성공 뒤 DB 저장 또는 포인트 반영 커밋이 실패하면 외부 결제 상태와 내부 결제·포인트 상태가 어긋난다. 반대로 DB 트랜잭션이 길어져 결제 row, 주문 row, SeniorProfile version 충돌 범위가 커진다.
- **깨질 수 있는 동작 또는 불변식:** PG 승인 완료 결제는 내부 Payment와 PointHistory에 정확히 한 번 반영된다. PG 취소 완료 결제는 내부 PaymentCancel과 포인트 환수에 정확히 한 번 반영된다.
- **목표 구조:** 외부 PG 호출과 내부 상태 반영의 경계를 명시하고, 실패 시 재조회·재조정 가능한 결제 동기화 정책을 둔다.
- **개선 방향:** 우선 PG 성공 후 내부 저장 실패, 포인트 낙관적 락 충돌, Feign timeout의 현재 동작을 Characterization Test로 고정한다. 이후 `CONFIRMING`/`POINT_APPLIED` 같은 내부 상태 또는 재조정 job을 둘지 결정한다. 당장 outbox나 saga를 도입하기보다 결제 상태 재조회·보정 경로부터 검토한다.
- **선행 테스트:** TEST-024.
- **예상 영향 범위:** 결제 승인·취소 API, 포인트 원장, PG 장애 대응.
- **추천 PR 단위:** `test(pay): characterize pg and point transaction boundary`.
- **관련 ADR·LLD:** LLD-0003.
- **결정 이력:** LLD-0003은 현재 구조에서 충돌 시 409와 클라이언트 재시도를 허용하는 것으로 기록한다. 다만 보정 경로는 아직 없다.
- **추가 확인 사항:** Toss confirm/cancel API의 동일 요청 재호출 응답 정책과 운영상 결제 대사 절차 확인 필요.

### ARCH-029 결제 승인 멱등성의 외부 중복 호출 위험

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `pay/application/PaymentService`, `pay/repository/PaymentRepository`, `pay/repository/PaymentOrderRepository`, `pay/Payment`, `pay/PaymentOrder`
- **확인된 사실:** `Payment.paymentKey`와 `PaymentOrder.orderId`는 unique이며, 승인 전 `findByOrderId()`와 `findByPaymentKey()`로 기존 결제를 조회한다. 중복 저장 시 `DataIntegrityViolationException`을 잡아 기존 결제를 반환한다. 다만 동시 승인 요청은 둘 다 기존 결제 없음으로 판단한 뒤 `paymentClient.confirmPayment()`를 호출할 수 있고, `PaymentOrder`에는 version이나 pessimistic lock 조회가 없다. pay 범위에서 webhook/callback 엔드포인트는 발견되지 않았다.
- **문제:** DB unique 제약은 내부 중복 저장을 막지만, 외부 PG 승인 API 중복 호출 자체를 막지는 못한다.
- **왜 문제인지:** 동일 orderId/paymentKey 재호출이 PG에서 완전 멱등하게 처리된다는 전제가 깨지면 중복 승인 오류, 불명확한 PG 상태, 내부 저장 실패가 발생할 수 있다. callback/webhook이 추가될 경우 사용자 요청과 callback이 같은 PaymentOrder/Payment를 동시에 변경할 가능성도 생긴다.
- **깨질 수 있는 동작 또는 불변식:** 같은 주문과 paymentKey의 결제 승인은 내부·외부 모두 정확히 한 번 처리된다. 재전송은 기존 결제 상태를 반환한다.
- **목표 구조:** 승인 처리 전 같은 orderId를 처리 중으로 점유하거나, 내부 상태를 `CONFIRMING`으로 전이한 뒤 PG 호출과 사후 반영을 구분한다.
- **개선 방향:** 먼저 동시 `confirmPayment()` 통합 테스트로 PG client 호출 횟수와 내부 저장 결과를 고정한다. 이후 PaymentOrder lock, 상태 전이, 멱등 요청 키 중 현재 규모에 맞는 선택지를 검토한다.
- **선행 테스트:** TEST-025.
- **예상 영향 범위:** 결제 승인 API, PaymentOrder 상태 전이, PG 장애 대응.
- **추천 PR 단위:** `fix(pay): guard duplicate payment confirmation`.
- **관련 ADR·LLD:** LLD-0003.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 향후 PG webhook/callback을 도입할 계획과 callback 멱등 키 확인 필요.

### ARCH-030 부분 취소 멱등성·동시성 모델 부재

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `pay/application/PaymentService`, `pay/Payment`, `pay/PaymentCancel`, `pay/repository/PaymentRepository`
- **확인된 사실:** `cancelPayment()`는 전액 취소되어 `PaymentStatus.CANCELED`인 경우만 PG 재호출 없이 기존 상태를 반환한다. 부분 취소는 `PaymentCancel` row를 추가하고 `Payment.canceledAmount`와 `canceledPointAmount`를 누적한다. `PaymentCancel`에는 PG cancel transaction key 또는 요청 멱등 키가 없고, `Payment`에도 version이나 lock 조회가 없다.
- **문제:** 같은 부분 취소 요청이 재전송되면 남은 금액 범위 안에서 다시 취소될 수 있고, 동시 취소 요청은 같은 remaining amount와 포인트 잔액을 기준으로 PG 취소를 중복 호출할 수 있다.
- **왜 문제인지:** 부분 취소는 금액·포인트 환수 비율을 누적 계산하므로 같은 요청의 중복 처리가 실제 환불·포인트 차감 중복으로 이어질 수 있다.
- **깨질 수 있는 동작 또는 불변식:** 같은 취소 요청은 한 번만 반영된다. 누적 취소 금액은 결제 금액을 넘지 않는다. 환수 포인트는 취소 금액 비율에 맞게 한 번만 차감된다.
- **목표 구조:** 취소 요청에는 멱등 키 또는 PG cancel transaction key를 저장하고, Payment 변경은 lock 또는 version으로 직렬화한다.
- **개선 방향:** 부분 취소 재전송과 병렬 취소 테스트를 먼저 추가한다. 이후 `PaymentCancel`에 cancel transaction key/요청 키를 저장하거나, Payment lock 조회로 취소 처리 구간을 보호한다.
- **선행 테스트:** TEST-026.
- **예상 영향 범위:** 결제 취소 API, 포인트 차감, 결제 내역 응답.
- **추천 PR 단위:** `fix(pay): make partial cancellation idempotent`.
- **관련 ADR·LLD:** LLD-0003.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 클라이언트가 취소 요청 멱등 키를 제공할 수 있는지, Toss cancel 응답에서 안정적인 cancel transaction key를 받을 수 있는지 확인 필요.

### ARCH-031 PaymentClient의 config 패키지 배치

- **상태:** 작업 예정
- **심각도:** Low
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `pay/config/PaymentClient`, `pay/config/PaymentFeignConfig`, `pay/config/PaymentAuthInterceptor`, `pay/config/PaymentErrorDecoder`, `pay/config/PaymentLoggingInterceptor`
- **확인된 사실:** 외부 PG Feign client 인터페이스인 `PaymentClient`가 Feign 설정, 인증 interceptor, error decoder와 함께 `pay/config` 패키지에 있다.
- **문제:** 설정 객체와 외부 PG adapter 계약이 같은 패키지에 섞여 있어, Application Service가 config 패키지의 client에 직접 의존한다.
- **왜 문제인지:** 패키지 이름이 책임을 드러내지 못하고, 향후 Toss 외 PG 교체·mock adapter·대사 client가 추가될 때 config와 infrastructure 경계가 더 혼재될 수 있다.
- **깨질 수 있는 동작 또는 불변식:** 직접적인 도메인 불변식보다 패키지 책임과 테스트 대체 가능성이 약해진다.
- **목표 구조:** `PaymentClient`는 `pay/infrastructure` 또는 `pay/client` 경계에 두고, Feign 설정은 config에 남긴다.
- **개선 방향:** 기능 변경 없이 패키지 이동만 수행하되, 아키텍처 경계 테스트(TEST-005)에 Controller/Service/Client 패키지 규칙을 추가한다.
- **선행 테스트:** TEST-005.
- **예상 영향 범위:** import 경로, 테스트 mock 타입.
- **추천 PR 단위:** `refactor(pay): move payment client to infrastructure package`.
- **관련 ADR·LLD:** LLD-0003.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 장기 구조에서 external-client 모듈을 둘지 여부 확인 필요.

### ARCH-032 mypage 조회·명령·외부 연동 책임 혼재

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `mypage/application/GuardianMyPageService`, `mypage/application/SeniorMyPageService`, `mypage/application/MyPageProfileService`, `location/parentlocation/repository/ParentLocationRepository`, `goal/addressbookmark/application/GeocodingService`
- **확인된 사실:** `GuardianMyPageService`는 보호자 프로필 조회, 이름·이미지·전화번호 변경, 시니어 추가, 시니어 프로필 수정, 가족 리더 변경, 가족 구성원 삭제를 한 Service에 포함한다. 이 과정에서 member/family Repository, auth Redis Repository, SMS, S3, geocoding, parentlocation Repository, pointHistory Repository를 직접 조합한다. `SeniorMyPageService`도 조회와 이름·이미지·전화번호·대표 연락처 변경을 함께 가진다.
- **문제:** 마이페이지 화면 조회, 가족 관리 명령, 회원 프로필 변경, 외부 연동 부수효과가 같은 Application Service에 섞여 있다.
- **왜 문제인지:** 단순 프로필 조회 변경과 가족 생명주기 정책 변경, S3/지오코딩 실패 정책이 같은 클래스에서 함께 변한다. 특히 프로필 이미지 변경과 주소 변경은 외부 호출이 트랜잭션 안에서 실행되고, 가족 접근·리더 검증 로직이 home/member 접근 정책과 중복된다.
- **깨질 수 있는 동작 또는 불변식:** 방장만 시니어 프로필과 가족 구성원을 변경한다. 가족에는 최소 1명의 시니어가 남는다. 프로필 이미지·주소 변경 실패 시 DB 상태와 외부 리소스 상태가 일관된다.
- **목표 구조:** 마이페이지 조회 Query Service와 회원/가족 변경 Command Service를 분리하고, S3·지오코딩은 명확한 실패 보상 정책을 가진 adapter 경계에서 호출한다.
- **개선 방향:** `GuardianMyPageService`의 조회 응답과 가족 변경 명령을 테스트로 고정한 뒤, 조회/명령 클래스를 분리한다. 가족 접근·리더 검증은 member/family 정책 API로 모으고, 주소 변경과 HOME 안전구역 갱신은 parentlocation 유스케이스로 위임한다.
- **선행 테스트:** TEST-028.
- **예상 영향 범위:** mypage guardian/senior API, member/family 생명주기, S3 프로필 이미지, parentlocation HOME 주소.
- **추천 PR 단위:** `refactor(mypage): split profile queries and family commands`.
- **관련 ADR·LLD:** ADR-0002, LLD-0001 일부 관련. mypage 전용 LLD는 없음.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 마이페이지에서 시니어 추가가 auth signup과 동일 정책을 공유해야 하는지 확인 필요.

### ARCH-033 admin 운영 조회의 OLTP 부하와 Projection 부재

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `admin/application/AdminDashboardService`, `admin/application/AdminMemberService`, `admin/application/AdminSearchService`, `admin/application/AdminAlbumService`, `admin/application/AdminPaymentService`, `admin/application/AdminFcmStatsService`
- **확인된 사실:** `AdminDashboardService`는 대시보드 한 번에 회원 count, 앨범 count, 결제 sum/count, 응급 count, 7일 회원·앨범 추이 쿼리를 순차 실행한다. `AdminMemberService`는 회원 상세에서 family, FCM token count, 최근 앨범, 최근 결제, 응급 count를 조합한다. `AdminSearchService`는 입력 형태에 따라 member/family/senior/payment Repository를 여러 번 조회한다. 대부분 Entity를 로딩해 DTO를 조립하고 전용 Projection은 거의 없다.
- **문제:** 운영 조회가 도메인 Repository와 운영 DB에 직접 부하를 주며, 화면별 필요한 컬럼만 조회하는 Projection/Read Model 경계가 약하다.
- **왜 문제인지:** 관리자 대시보드는 호출 빈도는 낮을 수 있지만 카운트·집계 쿼리가 많고 운영 DB의 주요 트랜잭션과 같은 테이블을 읽는다. 데이터가 증가하면 관리자 화면이 핵심 쓰기 트랜잭션 성능에 영향을 줄 수 있다.
- **깨질 수 있는 동작 또는 불변식:** 관리자 대시보드·검색·상세 응답의 집계 값과 최신성, 운영 DB의 쓰기 성능.
- **목표 구조:** admin은 도메인 Command Service가 아니라 운영 Read Model/관리 Command 경계로 분리하고, 대시보드·검색은 Projection Query Repository 또는 집계 cache/read model 후보로 관리한다.
- **개선 방향:** 현재 대시보드 응답 의미와 주요 쿼리 수를 통합 테스트로 고정한다. 이후 admin 전용 Query Repository를 만들고 필요한 컬럼만 Projection으로 조회한다. 대량 집계는 캐시 또는 별도 집계 테이블 필요성을 운영 데이터 규모 기준으로 판단한다.
- **선행 테스트:** TEST-029.
- **예상 영향 범위:** admin dashboard/search/member detail/album/payment/fcm stats API.
- **추천 PR 단위:** `test(admin): characterize dashboard and search queries`.
- **관련 ADR·LLD:** LLD-0003 일부 관련. admin 전용 ADR·LLD는 없음.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 관리자 화면 호출 빈도, 운영 DB 인덱스, 데이터 보존 기간 확인 필요.

### ARCH-034 AdminAuditLog 저장 정책의 트랜잭션 일관성 차이

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** `admin/AdminAuditLog`, `admin/application/AdminAuditLogService`, `admin/application/AdminMemberService`, `admin/application/AdminAuthService`, `admin/application/AdminFcmService`
- **확인된 사실:** `AdminAuditLogService.log()`는 `REQUIRES_NEW`로 별도 트랜잭션에 감사 로그를 저장한다. `AdminMemberService.changeStatus()`와 `AdminFcmService.sendTestNotification()`은 이 서비스를 사용한다. 반면 `AdminAuthService.login()`은 `AdminAuditLogRepository`를 직접 호출해 로그인 로그를 같은 트랜잭션에서 저장한다.
- **문제:** 어떤 감사 로그는 본 작업 트랜잭션과 독립적으로 커밋되고, 어떤 로그는 본 작업과 함께 커밋된다. 정책이 코드 위치마다 다르다.
- **왜 문제인지:** 본 작업이 롤백되어도 `REQUIRES_NEW` 로그는 남을 수 있고, 로그인 로그는 인증 트랜잭션 실패 시 남지 않는다. 감사 로그가 "시도 기록"인지 "성공 기록"인지 명확하지 않다.
- **깨질 수 있는 동작 또는 불변식:** 관리자 중요 작업 감사 로그는 정책에 맞게 누락되거나 과잉 기록되지 않는다.
- **목표 구조:** AdminAuditLog의 의미를 성공 로그, 시도 로그, 실패 로그 중 무엇으로 볼지 정하고 모든 admin 작업이 같은 기록 경계를 사용한다.
- **개선 방향:** 성공 작업만 기록하는 현재 의미를 유지할지, 실패 시도까지 기록할지 결정한다. 이후 `AdminAuthService`도 `AdminAuditLogService`를 통해 기록하고, `REQUIRES_NEW` 사용 여부를 문서화한다.
- **선행 테스트:** TEST-030.
- **예상 영향 범위:** admin login, member status change, FCM test send, 감사 로그 조회.
- **추천 PR 단위:** `refactor(admin): standardize audit log transaction policy`.
- **관련 ADR·LLD:** 문서 없음.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 감사 로그 보존 요구와 실패 시도 기록 필요 여부 확인 필요.

### ARCH-004 global의 member 역방향 의존성

- **상태:** 작업 예정
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** [MemberUtil.java](../../backend/widyu-api/src/main/java/com/widyu/global/util/MemberUtil.java), [FamilyAccessAspect.java](../../backend/widyu-api/src/main/java/com/widyu/global/aspect/FamilyAccessAspect.java)
- **확인된 사실:** global의 `MemberUtil`과 `FamilyAccessAspect`가 Member Entity 및 member Repository를 직접 참조한다.
- **문제:** global이 범용 인프라가 아니라 member Application 정책 일부가 된다.
- **왜 문제인지:** member가 global 오류·보안 유틸을 사용하여 사실상 순환이 생긴다.
- **깨질 수 있는 동작 또는 불변식:** 현재 사용자 조회, 보호자-시니어 가족 접근 검증.
- **목표 구조:** global은 보안 컨텍스트·HTTP 공통 처리만 보유하고, member 관련 정책은 member에 둔다.
- **개선 방향:** `MemberUtil`을 `member.application.support`, 가족 접근 Aspect와 annotation을 `member.authorization.web`로 이동하는 방안을 검토한다.
- **선행 테스트:** TEST-004, TEST-005.
- **예상 영향 범위:** auth, member, AOP 적용 Controller.
- **추천 PR 단위:** `가족 접근 정책 단일화`.
- **관련 ADR·LLD:** [ADR-0002](../adr/ADR-0002-auth-jwt-family-access.md).
- **결정 이력:** 패키지 이동은 제안이며 아직 미결정.
- **추가 확인 사항:** `TemporaryMemberUtil`의 global -> auth 의존성도 같은 기준으로 후속 검토 필요.

### ARCH-005 화면 유스케이스의 다도메인 Repository 조합

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `home/application/GuardianHomeService`, `home/application/SeniorHomeService`, `home/application/HomeAlbumRecommendationService`, `admin/application/AdminDashboardService`, `admin/application/AdminMemberService`, `admin/application/AdminSearchService`, `member/repository`, `album/repository`, `pay/repository`
- **확인된 사실:** `GuardianHomeService`는 member/family, heart, medicine, medication proof, healthschedule, walk, album 추천을 한 응답으로 조합한다. `SeniorHomeService`도 medicine, medication proof, walk, healthschedule, heart, album을 직접 조합한다. `AdminDashboardService`는 member, family membership, album, payment, heart emergency Repository를 여러 번 호출해 카운트와 7일 추이를 만든다. `AdminMemberService`와 `AdminSearchService`는 member/family/album/payment/fcm/heart Repository를 조합한다.
- **문제:** 화면 응답 조립 규칙과 도메인 Repository 호출 순서가 Service에 누적되고, 도메인 Repository에 `findAllForAdmin`, `findTopScoredAlbumIdsByMemberIds`, `countActiveAlbumsCreatedBetween`, `findTop20ByNameContainingOrderByIdDesc` 같은 화면·운영 조회 계약이 섞인다.
- **왜 문제인지:** 화면 요구 변경이 도메인 Repository 계약에 직접 반영되고, 여러 화면 Service가 같은 가족·회원 조회를 반복한다. Projection이 아닌 Entity 로딩 후 DTO 조립이 많아 조회 성능과 N+1 위험을 테스트하기 어렵다.
- **깨질 수 있는 동작 또는 불변식:** 화면별 조회 결과 및 권한 필터.
- **목표 구조:** 화면 조합 Service를 명시적인 query/read model 경계로 관리한다.
- **개선 방향:** home/admin부터 응답 의미를 Characterization Test로 고정하고, 화면 전용 Query Repository 또는 Projection DTO 조회로 분리한다. 가족 구성원 조회는 공통 가족 query policy로 중복을 줄인다.
- **선행 테스트:** TEST-027, TEST-029.
- **예상 영향 범위:** home, mypage, admin.
- **추천 PR 단위:** `test(home): characterize dashboard query composition`.
- **관련 ADR·LLD:** LLD-0007, LLD-0008, LLD-0003 일부 관련. home/mypage/admin 전용 ADR·LLD는 확인되지 않았다.
- **결정 이력:** 상세 리뷰에서 home/mypage/admin은 Aggregate보다 화면·조회 유스케이스로 기록한다.
- **추가 확인 사항:** 운영 트래픽에서 home/dashboard 호출 빈도와 허용 쿼리 수 확인 필요.

### ARCH-006 Controller 외부 API 직접 호출 예외

- **상태:** 분석 중
- **심각도:** Medium
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `auth/controller/NaverLoginController`
- **확인된 사실:** 테스트 성격의 Naver Controller가 `RestTemplate`으로 토큰 API를 직접 호출한다.
- **문제:** 웹 계층에 외부 연동과 응답 해석이 남아 있다.
- **왜 문제인지:** OAuth Strategy/Client 경계가 일관되지 않다.
- **깨질 수 있는 동작 또는 불변식:** OAuth 테스트 엔드포인트 동작.
- **목표 구조:** 외부 연동은 auth Strategy 또는 Client 계층에 둔다.
- **개선 방향:** 테스트 엔드포인트의 유지 필요성을 먼저 확인하고, 유지 시 기존 Strategy/Client 경계로 이동한다.
- **선행 테스트:** auth 상세 API 계약 테스트.
- **예상 영향 범위:** auth 테스트 엔드포인트.
- **추천 PR 단위:** `Naver 테스트 연동 계층 정리`.
- **관련 ADR·LLD:** ADR-0002, LLD-0004 참고.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 실제 운영 노출 여부 확인 필요.

### ARCH-007 아키텍처 경계 자동 검증 부재

- **상태:** 작업 예정
- **심각도:** Low
- **관련 모듈:** 전체
- **관련 패키지 및 파일:** Gradle 테스트 구성
- **확인된 사실:** ArchUnit 의존성·구조 테스트는 확인되지 않았다.
- **문제:** 모듈·계층 규칙의 회귀를 자동 방지하지 못한다.
- **왜 문제인지:** Controller->Repository, API의 Entity, domain의 Repository 위반은 기능 테스트만으로 포착하기 어렵다.
- **깨질 수 있는 동작 또는 불변식:** 구조 규칙 자체.
- **목표 구조:** 핵심 구조 규칙이 CI에서 검증된다.
- **개선 방향:** ArchUnit 또는 동등한 구조 테스트를 도입한다.
- **선행 테스트:** TEST-005.
- **예상 영향 범위:** 테스트 의존성, CI.
- **추천 PR 단위:** `아키텍처 경계 테스트`.
- **관련 ADR·LLD:** 없음.
- **결정 이력:** 미결정.
- **추가 확인 사항:** 기존 harness 검사와 중복되지 않는 최소 규칙 선정 필요.

### ARCH-008 Refresh Token 회전 검증 누락

- **상태:** 완료 (2026-07-15, 브랜치 fix/#395, 이슈 #395)
- **심각도:** Critical
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** [JwtTokenProvider.java](../../backend/widyu-api/src/main/java/com/widyu/global/security/JwtTokenProvider.java), [GuardianTokenService.java](../../backend/widyu-api/src/main/java/com/widyu/auth/application/guardian/GuardianTokenService.java), [RefreshToken.java](../../backend/widyu-domain/src/main/java/com/widyu/auth/RefreshToken.java)
- **확인된 사실:** Refresh Token 검증은 Redis 키 존재만 확인하고 저장된 token 값과 요청 token 값을 비교하지 않는다. 재발급 시 새 토큰을 두 차례 저장한다.
- **문제:** 회전 이전의 서명 유효 Refresh Token이 Redis 키가 존재하는 동안 재발급에 재사용될 수 있다.
- **왜 문제인지:** 토큰 회전의 핵심 불변식이 깨져 탈취된 이전 토큰의 유효 범위가 불필요하게 길어진다.
- **깨질 수 있는 동작 또는 불변식:** 최신 Refresh Token만 재발급 가능해야 한다.
- **목표 구조:** memberId당 저장된 현재 token 값 하나만 유효하고 재발급은 한 번의 저장으로 끝난다.
- **개선 방향:** 저장 token과 요청 token을 비교하고, `GuardianTokenService`의 이중 발급 경로를 하나로 합친다.
- **선행 테스트:** TEST-001.
- **예상 영향 범위:** 로그인, 재발급, 로그아웃, Redis 인증 상태.
- **추천 PR 단위:** `Refresh Token rotation 검증 수정`.
- **관련 ADR·LLD:** ADR-0002.
- **결정 이력:** 2026-07-15 리뷰에서 확인. 2026-07-15 구현 완료 — `JwtTokenProvider.validateRefreshTokenMatches()`가 Redis 저장 token 값과 요청 token 값을 비교하고, `GuardianTokenService.reissueTokenPair()`의 `createRefreshTokenDto()` 이중 발급 호출을 제거했다. 설계: [ARCH-008 구현 설계](implementation-plans/ARCH-008-refresh-token-rotation.md).
- **추가 확인 사항:** Refresh Token 쿠키 전달 방식의 실제 구현은 ADR 정합성 검토에서 확정한다. 리뷰에서 나온 범위 외 후속 후보: ① `JwtTokenProvider.createRefreshTokenDto()`는 운영 호출처 0개인 미사용 메서드로 남아 있음(테스트 `verify(never())` 가드가 참조) — 별도 정리 PR 후보. ② 동시 재발급 두 요청이 같은 최신 token으로 들어오는 경우의 CAS 수준 원자성은 단일/다중 세션 정책 결정과 함께 별도 설계 필요.

### ARCH-009 Senior 가입 행위자 타입 검증 누락

- **상태:** 작업 예정
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** [SeniorAuthService.java](../../backend/widyu-api/src/main/java/com/widyu/auth/application/senior/SeniorAuthService.java), [SecurityConfig.java](../../backend/widyu-api/src/main/java/com/widyu/global/config/SecurityConfig.java)
- **확인된 사실:** `seniorSignUpBulk()`는 FamilyMembership 존재만 확인하며 현재 Member가 `GUARDIAN`인지 확인하지 않는다. Spring Security는 일반 사용자를 같은 `USER` role로 처리한다.
- **문제:** Family에 속하지 않은 시니어가 인증 토큰으로 Family, SeniorProfile, leader Membership을 생성할 수 있다.
- **왜 문제인지:** 행위자 타입은 유스케이스 권한 정책인데 웹 role만으로 구분되지 않는다.
- **깨질 수 있는 동작 또는 불변식:** Family 생성과 leader 등록은 보호자만 수행한다.
- **목표 구조:** Senior 등록 유스케이스가 Service 경계에서 guardian type을 보장한다.
- **개선 방향:** `MemberType.GUARDIAN` 검증을 추가하고 오류 계약을 테스트한다.
- **선행 테스트:** TEST-002.
- **예상 영향 범위:** senior 가입 API, Family 초기 생성.
- **추천 PR 단위:** `시니어 등록 행위자 검증`.
- **관련 ADR·LLD:** ADR-0002.
- **결정 이력:** 2026-07-15 리뷰에서 확인.
- **추가 확인 사항:** admin의 등록 허용 여부는 별도 정책 확인 필요.

### ARCH-010 탈퇴 후 FamilyMembership·leader 상태 잔존

- **상태:** 개선안 제안
- **심각도:** High
- **관련 모듈:** `widyu-api`, `widyu-domain`
- **관련 패키지 및 파일:** [MemberWithdrawService.java](../../backend/widyu-api/src/main/java/com/widyu/auth/application/guardian/MemberWithdrawService.java), [FamilyMembershipRepository.java](../../backend/widyu-api/src/main/java/com/widyu/member/repository/FamilyMembershipRepository.java)
- **확인된 사실:** 탈퇴는 Member 비활성화·개인정보 마스킹을 수행하지만 FamilyMembership을 삭제 또는 비활성화하지 않는다. leader 조회는 Member 상태를 고려하지 않는다.
- **문제:** 비활성 guardian이 Family leader로 남을 수 있다.
- **왜 문제인지:** Membership은 현재 Family나 Member의 cascade 생명주기에 속하지 않는 연결 Aggregate이며, 탈퇴 정책이 명시되지 않았다.
- **깨질 수 있는 동작 또는 불변식:** 유효한 guardian만 Family 구성원·leader여야 한다는 규칙.
- **목표 구조:** 탈퇴·leader 위임·membership 삭제 또는 비활성화 정책이 명시된다.
- **개선 방향:** 제품 정책을 결정한 뒤 Membership 처리와 leader 조회 조건을 같은 PR에서 변경한다.
- **선행 테스트:** TEST-003, TEST-007.
- **예상 영향 범위:** 탈퇴, Family 참여, 가족 접근 검사.
- **추천 PR 단위:** `탈퇴-가족 구성원 생명주기 정책`.
- **관련 ADR·LLD:** ADR-0002.
- **결정 이력:** 미결정.
- **추가 확인 사항:** Family 삭제, leader 승계, 감사 보존 요구사항 확인 필요.

### ARCH-011 가족 접근 정책의 AOP·Service 분산

- **상태:** 작업 예정
- **심각도:** High
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** [FamilyAccessAspect.java](../../backend/widyu-api/src/main/java/com/widyu/global/aspect/FamilyAccessAspect.java), [FamilyMembershipRepository.java](../../backend/widyu-api/src/main/java/com/widyu/member/repository/FamilyMembershipRepository.java)
- **확인된 사실:** Aspect 외에도 home, location, heart Service가 가족 연결 Repository predicate를 직접 호출한다. Aspect는 일부 Controller에만 적용된다.
- **문제:** 정책 구현과 예외 의미가 여러 진입점에 분산된다.
- **왜 문제인지:** AOP는 HTTP 적용 방식이지 가족 관계 자체의 유일한 정책 소유자가 될 수 없다.
- **깨질 수 있는 동작 또는 불변식:** 보호자는 연결된 시니어에게만 접근한다.
- **목표 구조:** member/family Application 정책이 단일 진실 공급원이 되고 Aspect는 이를 호출하는 웹 어댑터가 된다.
- **개선 방향:** `FamilyAccessService`를 추출해 Aspect와 동기 Service 호출이 공유하도록 한다.
- **선행 테스트:** TEST-004.
- **예상 영향 범위:** AOP 적용 Controller, home, location, heart.
- **추천 PR 단위:** `가족 접근 정책 단일화`.
- **관련 ADR·LLD:** ADR-0002.
- **결정 이력:** 패키지 이동과 정책 추출은 제안.
- **추가 확인 사항:** senior->guardian 메시지 정책 등 비대칭 권한을 동일 API에 포함할지 확인 필요.

### ARCH-012 가입 트랜잭션 내부 외부 지오코딩 호출

- **상태:** 개선안 제안
- **심각도:** Medium
- **관련 모듈:** `widyu-api`
- **관련 패키지 및 파일:** `auth/application/senior/SeniorAuthService`
- **확인된 사실:** Family·Member·SeniorProfile·Membership 생성 트랜잭션 안에서 `GeocodingService`와 ParentLocation Repository를 사용한다.
- **문제:** 외부 네트워크 대기 시간이 DB 트랜잭션을 점유한다.
- **왜 문제인지:** 가입과 HOME 안전구역 생성의 동기 일관성 정책이 외부 인프라 지연에 결합된다.
- **깨질 수 있는 동작 또는 불변식:** 현재는 지오코딩 실패 시 가입 전체가 실패한다.
- **목표 구조:** 이 정책을 유지한다면 외부 검증과 DB 트랜잭션 경계를 분리한다.
- **개선 방향:** 지오코딩을 트랜잭션 시작 전 검증하고 저장은 짧은 트랜잭션으로 수행한다. 비동기 전환은 요구사항 변경 후 검토한다.
- **선행 테스트:** TEST-006.
- **예상 영향 범위:** auth, addressbookmark, parentlocation.
- **추천 PR 단위:** `시니어 가입 지오코딩 경계`.
- **관련 ADR·LLD:** 확인 필요.
- **결정 이력:** 미결정.
- **추가 확인 사항:** HOME 안전구역 생성이 가입 완료의 필수 조건인지 제품 정책 확인 필요.

## 5. 도메인별 리뷰

### 5.1 member/family/auth

- **리뷰 상태:** 상세 리뷰 완료.
- **검토한 파일:** `Member`, `Family`, `FamilyMembership`, `SeniorProfile`, `LocalAccount`, `SocialAccount`, `RefreshToken`, `TemporaryMember`, 관련 Repository·Service·AOP·테스트. 상세 목록은 1절 참조.
- **현재 도메인 경계:** Member는 계정 보유자, Family는 가족 식별자, FamilyMembership은 guardian-Family 연결, SeniorProfile은 시니어 프로필·포인트 상태를 담당한다.
- **Aggregate Root 후보:** `Member`, `Family`, `SeniorProfile`, `FamilyMembership`.
- **Aggregate 내부 Entity 후보:** Member 하위 `LocalAccount`, `SocialAccount`.
- **독립 Aggregate 후보:** `SeniorProfile`, `FamilyMembership`; `PointHistory`는 SeniorProfile 원장이지만 별도 Repository로 관리된다.
- **핵심 불변식:** guardian 한 Family 소속, guardian만 Family·Senior 생성, 최신 Refresh Token만 재발급, 연결 guardian만 senior 리소스 접근, 포인트 잔액 음수 금지.
- **현재 불변식 보장 위치:** DB unique, Service 사전 조회·조건문, `FamilyAccessAspect`, `SeniorProfile.@Version` 및 재시도.
- **다른 도메인과의 의존성:** auth는 address geocoding·parent location을 직접 사용하고, member는 album unlock 조회를 직접 사용한다.
- **동기 호출 유지 대상:** 가입 시 Family·Member·SeniorProfile·Membership 생성, 포인트 잔액과 원장 기록.
- **이벤트 분리 후보:** 탈퇴 후 알림·외부 revoke의 후속 처리 여부는 정책 확인 후 검토.
- **Read Model 후보:** 시니어 해금 앨범 ID 조회, guardian 프로필/연결 상태 조회.
- **주요 문제:** ARCH-008~012.
- **유지해야 할 구조:** Member-LocalAccount/SocialAccount 구성 관계, SeniorProfile 포인트 낙관적 락과 원장 기록.
- **개선 방향:** Refresh Token 검증을 우선 수정한 뒤, 가입 행위자·Membership 생명주기·가족 접근 정책을 정리한다.
- **선행 테스트:** TEST-001~004, TEST-006~007.
- **추천 PR:** 10절 참조.
- **관련 ADR·LLD:** ADR-0002, LLD-0004.
- **미확정 사항:** inviteCode 전역 유일성, 탈퇴 guardian의 Family 보존 및 leader 승계 정책.

### 5.2 location/heart

- **리뷰 상태:** 상세 리뷰 완료.
- **검토한 파일:** `SeniorLocation`, `ParentLocation`, `HeartRateEvent`, `HeartRateEmergency`, `HeartRateResult`, 관련 Repository, `RealtimeLocationService`, `ParentLocationService`, `HeartRateService`, `HeartMessageService`, `HeartRateAnomalyDetector`, WebSocket Controller·Config·Interceptor, 안전구역·건강일정 이벤트 리스너와 관련 테스트.
- **현재 도메인 경계:** `SeniorLocation`은 5분 TTL 최신 위치, `location:trail`은 15분 이동 이력, `location:stay`는 24시간 체류 상태인 별도 Redis 모델이다. `ParentLocation`은 영속 안전구역 설정이며 `SeniorLocation`과 역할·생명주기가 다르다. 심박 Event는 이력, Emergency는 독립 응급기록, Result는 24시간 최신 상태 Read Model이다.
- **Aggregate Root 후보:** `ParentLocation`은 시니어별 안전구역 설정 Aggregate, `HeartRateEvent`와 `HeartRateEmergency`는 현재 별도 Aggregate 후보이다. `SeniorLocation`은 Aggregate가 아닌 최신 상태 캐시다.
- **Aggregate 내부 Entity 후보:** 확인된 대상에 없음.
- **독립 Aggregate 후보:** `ParentLocation`, `HeartRateEvent`, `HeartRateEmergency`.
- **핵심 불변식:** 연결 보호자만 안전구역을 변경·구독한다. 안전구역 이탈은 30분 내 한 번만 알린다. 측정 배치당 응급기록은 한 번만 생성한다.
- **현재 불변식 보장 위치:** 위치 갱신 본인 검증과 REST 조회 가족 검증은 Service에 있다. ParentLocation CUD와 STOMP 구독에는 가족 검증이 없고, 안전구역·응급기록 중복은 원자적으로 보장되지 않는다.
- **다른 도메인과의 의존성:** location은 parentlocation을 동기 조회하고 healthschedule에는 비동기 내부 이벤트를 발행하며, safezone FCM은 동기 리스너로 호출한다. heart는 member·family Repository와 AI HTTP 연동을 직접 사용한다.
- **동기 호출 유지 대상:** 위치 수신의 본인 검증, 최신 위치·trail·stay 갱신, 안전구역 반경 판정, 심박 판정 결과 저장.
- **이벤트 분리 후보:** 안전구역 FCM·WebSocket 전달 후속 처리. `SeniorLocationUpdatedEvent` 기반 healthschedule 처리의 비동기 분리는 유지한다.
- **Read Model 후보:** `SeniorLocation`, trail·stay Redis 키, `HeartRateResult`.
- **주요 문제:** ARCH-003, ARCH-013~018. ARCH-004·011의 가족 접근 정책 분산이 ParentLocation·STOMP에도 영향을 준다.
- **유지해야 할 구조:** 최신 위치·이력·체류 상태의 Redis 분리, `HeartRateResult` 최신 상태 Read Model, healthschedule 방문인증의 비동기 후속 처리.
- **개선 방향:** ParentLocation·STOMP 접근 제어를 먼저 고정하고, 안전구역·심박 재전송 멱등성 테스트 후 외부 부수효과와 AI 트랜잭션 경계를 분리한다.
- **선행 테스트:** TEST-008~013.
- **추천 PR:** 10절의 location/heart 예비 후보.
- **관련 ADR·LLD:** ADR-0002, ADR-0007, LLD-0001, LLD-0002, LLD-0009.
- **미확정 사항:** 다중 인스턴스 운영 여부, 심박 이상 FCM 요구사항, AI 장애 시 원본 심박 기록 보존 정책.

### 5.3 goal/medicine/walk/healthschedule

- **리뷰 상태:** 상세 리뷰 완료.
- **검토한 파일:** `HealthSchedule`, `Medicine`, `MedicineCategory`, `MedicineSchedule`, `MedicineScheduleDetail`, `MedicationProof`, `Walk`, `DailyGoalStatus`, `HealthScheduleFacadeImpl`, `HealthScheduleService`, `HealthScheduleProgressService`, `HealthScheduleRewardService`, `HealthScheduleLocationEventListener`, `HealthScheduleScheduler`, `MedicineScheduleService`, `MedicationProofService`, `ExternalMedicineService`, `MedicineScheduleRewardScheduler`, `MedicineSyncScheduler`, `MedicineScheduleNotificationListener`, `HealthScheduleNotificationListener`, `WalkNotificationListener`, `WalkService`, `GoalHomeService`, 관련 Repository·Controller·테스트, ADR-0006·0007, LLD-0003·0005·0007·0008·0009.
- **현재 도메인 경계:** `medicine`, `walk`, `healthschedule`은 하나의 단일 Aggregate가 아니라 건강관리 상위 기능 아래의 독립 하위 유스케이스에 가깝다. `goal/home`은 쓰기 도메인보다 약·걷기·건강일정 상태를 조합하는 Read Model/화면 조회 계층이다.
- **Aggregate Root 후보:** `HealthSchedule`, `MedicineSchedule`, `Medicine`, `MedicationProof`, `Walk`.
- **Aggregate 내부 Entity 후보:** `MedicineSchedule` 내부의 `MedicineCategory`, `MedicineScheduleDetail`. `MedicineSchedule`은 category/detail을 cascade + orphanRemoval로 관리한다.
- **독립 Aggregate 후보:** `MedicationProof`는 schedule에 종속된 수행 기록처럼 보이나, 시간이 지날수록 증가하고 포인트 정산·통계의 원천이므로 독립 실행 기록 후보로 분리 검토가 필요하다. `Medicine`은 공공 약품 마스터 데이터 Aggregate 후보다. `Walk`는 회원·일자 단위 기록 Aggregate 후보다.
- **핵심 불변식:** 건강 일정은 당일 00시부터 예정 시각 + 30분까지, 일정 장소 75m 안에서만 완료된다. 복약 스케줄 수정은 과거 표시를 바꾸지 않고 유효기간 버전으로 보존한다. 회원은 같은 복약 스케줄을 같은 날짜에 한 번만 인증해야 한다. 걷기 기록은 회원·일자당 하나이고, 목표 달성 보상은 중복 지급되지 않아야 한다. 건강관리 보상은 포인트 잔액과 원장이 일관되어야 한다.
- **현재 불변식 보장 위치:** HealthSchedule 시간창과 표시 상태는 Entity 메서드, 위치 반경과 가족 접근은 Service 조건문, 복약 유효기간은 Entity 메서드와 Repository 쿼리, 복약 인증 중복은 Repository `exists` 선조회, Walk 일자 중복은 DB unique 제약, 포인트 경쟁은 일부 `SeniorProfile.@Version`과 `@RetryOnPointConflict`로 보장된다.
- **다른 도메인과의 의존성:** healthschedule은 member/family Repository와 location Redis 최신 위치를 직접 조회하고, location event를 비동기로 수신한다. medicineschedule은 member Repository, S3, FCM Scheduler, 공공 의약품 API를 사용한다. walk는 member/SeniorProfile 포인트를 직접 변경한다. GoalHomeService는 healthschedule·medicineschedule·walk·member/family Repository를 직접 조합한다.
- **동기 호출 유지 대상:** 수동 건강 일정 완료 시 최신 위치 조회와 반경 검증, MedicineSchedule 생성·수정 시 약품 마스터 조회, Walk 걸음 수 동기화와 당일 목표 상태 변경.
- **이벤트 분리 후보:** 건강 일정 자동 완료는 이미 `SeniorLocationUpdatedEvent` 비동기 리스너로 분리되어 있으며 유지한다. 건강관리 포인트 지급, 복약 미복용 보호자 알림, 걷기 미달성 알림은 실패·중복 정책을 명시한 후 후속 처리 경계로 분리 검토한다.
- **Read Model 후보:** `GoalHomeService`의 시니어/보호자 목표 홈, 복약 월별 달성률, 걷기 월별 조회, 건강 일정 캘린더.
- **주요 문제:** ARCH-019~022. HealthSchedule의 위치 이벤트 분리는 ADR-0007·LLD-0009와 일치하지만 실패 재시도는 명시적 보류 상태다.
- **유지해야 할 구조:** MedicineSchedule 유효기간 버전링과 category/detail cascade 경계, `Medicine` 약품 마스터의 DB 우선 조회 + 외부 API fallback, 위치 기반 건강 일정 완료의 비동기 internal event 경계, Walk의 회원·일자 unique 제약.
- **개선 방향:** 보상 정책과 멱등성부터 테스트로 고정한다. 그 다음 복약 인증 unique/파일 업로드 경계, 복약 정산 원장, GoalHome 조회 경계를 순차 정리한다. API 경로 재명명은 클라이언트 영향이 크므로 당장 추진하지 않고 문서상 컨텍스트를 먼저 분리한다.
- **선행 테스트:** TEST-014~018.
- **추천 PR:** 10절의 건강관리 예비 후보.
- **관련 ADR·LLD:** ADR-0006, ADR-0007, LLD-0005, LLD-0007, LLD-0008, LLD-0009.
- **미확정 사항:** 건강 일정 실제 포인트 지급 요구사항, 걷기 보상 PointHistory 필요 여부, 복약 정산 다중 인스턴스 중복 실행 가능성, `goal` 패키지 재명명 가능성.

### 5.4 album/fcm

- **리뷰 상태:** 상세 리뷰 완료.
- **검토한 파일:** `Album`, `AlbumComment`, `AlbumLike`, `AlbumView`, `AlbumUnlock`, `MediaType`, `FcmNotification`, `MemberFcmToken`, `MemberNotificationSetting`, `FcmCategory`, `NotificationSettingGroup`, `AlbumFacadeImpl`, `AlbumService`, `AlbumLikeService`, `AlbumViewService`, `AlbumCommentService`, `AlbumUnlockService`, `AlbumPermissionService`, `AlbumFeedService`, `AlbumFileService`, `AlbumVideoProcessingService`, `AlbumMediaPolicy`, `FFmpegVideoCompressionService`, `S3ServiceImpl`, `FcmService`, `MemberFcmTokenService`, `NotificationSettingService`, `AlbumNotificationListener`, album/fcm 관련 Repository, album/fcm 관련 Event DTO, `AlbumServiceTest`, `AlbumLikeServiceTest`, `AlbumViewServiceTest`, `AlbumUnlockServiceTest`, `AlbumCommentServiceTest`, `AlbumFileServiceTest`, `AlbumNotificationListenerTest`, `NotificationSettingServiceTest`, `AlbumMediaPolicyTest`, ADR-0004·0005, LLD-0002·0006.
- **현재 도메인 경계:** `album`은 콘텐츠·미디어·상호작용·잠금 해제·영상 처리 상태를 함께 다루는 Application 경계다. `fcm`은 회원별 토큰·알림 설정·알림 저장을 갖지만, 현재 구조상 독립 비즈니스 도메인보다 알림 인프라 저장 모델과 후속 처리 어댑터에 가깝다.
- **Aggregate Root 후보:** `Album`, `AlbumComment`, `AlbumLike`, `AlbumView`, `AlbumUnlock`, `MemberFcmToken`, `MemberNotificationSetting`, `FcmNotification`.
- **Aggregate 내부 Entity 후보:** `Album` 내부에는 미디어 URL·썸네일·duration element collection과 처리 상태가 자연스럽게 포함된다. 현재 `Album`은 `comments`, `likes`, `views`도 컬렉션으로 갖지만 대량 증가하는 상호작용이므로 내부 Entity로 유지할지 재검토가 필요하다.
- **독립 Aggregate 후보:** `AlbumLike`, `AlbumView`, `AlbumUnlock`은 회원·앨범 unique 제약을 가진 독립 상호작용 기록 후보가 강하다. `AlbumComment`는 답글을 cascade로 보유하므로 댓글 스레드 Root 후보이며, `AlbumUnlock`은 포인트 차감·PointHistory와 연결되어 독립 수행 기록에 가깝다. `FcmNotification`, `MemberFcmToken`, `MemberNotificationSetting`은 FCM 인프라 저장 모델 후보로 별도 adapter 경계 검토가 필요하다.
- **핵심 불변식:** 앨범 영상은 PROCESSING에서 ACTIVE 또는 실패 상태로 전이된다. 한 회원은 같은 앨범에 좋아요·조회·잠금 해제를 중복 생성하지 않는다. 앨범 카운터는 실제 상호작용 의미와 일치해야 한다. senior는 충분한 포인트가 있을 때만 유료 앨범을 잠금 해제하고 중복 차감되지 않아야 한다. 알림 실패는 앨범 쓰기 성공을 되돌리지 않아야 한다. 하나의 활성 FCM 토큰은 정확히 한 회원에게 귀속되어야 한다.
- **현재 불변식 보장 위치:** Like/View/Unlock 중복은 Repository `exists` 선조회와 DB unique 제약에 분산되어 있다. 카운터는 Service에서 `Album.increment*()`/`decrement*()` 호출로 갱신된다. 영상 처리 상태 전이는 `Album.completeVideoProcessing()`과 `Album.delete()`에서 수행된다. 잠금 해제 포인트 차감은 `AlbumUnlockService`가 `SeniorProfile.deductPoints()`와 `PointHistory` 저장을 직접 조합한다. 알림 수신 설정은 `FcmService`가 전송 직전에 조회한다.
- **다른 도메인과의 의존성:** album은 member Entity/Repository, `SeniorProfile`, `PointHistory`, S3, FFmpeg, fcm event DTO를 직접 사용한다. fcm은 member Repository, family Repository, album Repository/View Repository를 직접 조회해 수신자와 toast count를 계산한다.
- **동기 호출 유지 대상:** 앨범 저장 시 작성자 확인, 이미지·영상 파일 기본 검증, 잠금 해제 시 senior 포인트 차감과 `AlbumUnlock` 저장은 현재 사용자 응답과 강한 일관성이 필요하다.
- **이벤트 분리 후보:** 앨범 생성·좋아요·댓글·조회·잠금 해제 알림은 커밋 이후 후속 처리 후보다. 영상 처리 완료 후 앨범 생성 알림은 처리 성공 커밋 이후 발송되어야 한다. FCM 전달 재시도와 outbox는 전달 보장 수준 결정 후 검토한다.
- **Read Model 후보:** 앨범 피드 cursor 조회, 인기 앨범 점수, top viewer 목록, FCM toast의 미조회 앨범 수, 알림 목록.
- **주요 문제:** ARCH-023~027. 특히 Album 이벤트가 fcm 패키지 DTO에 의존하고 동기 `@EventListener`로 FCM을 호출하는 구조는 앨범 쓰기와 외부 알림 실패를 결합한다.
- **유지해야 할 구조:** 영상 포함 앨범을 PROCESSING으로 먼저 저장하고 비동기로 ACTIVE 전환하는 흐름, 이미지 즉시 업로드와 영상 후처리 분리, cursor pagination에서 id 선조회 후 컬렉션 fetch로 N+1을 줄이는 조회 방식, FCM 알림 설정을 전송 직전에 확인하는 정책.
- **개선 방향:** 상호작용 멱등성과 카운터를 먼저 통합 테스트로 고정한다. 앨범 접근 범위를 제품 정책으로 확정하고, 영상 처리 실패·재시도·S3 보상 삭제 정책을 LLD-0006의 보류 리스크와 함께 정리한다. 알림 이벤트는 album/application 소유 이벤트로 옮기고 FCM은 AFTER_COMMIT 후속 처리 어댑터로 격리한다.
- **선행 테스트:** TEST-019~023.
- **추천 PR:** 10절의 album/fcm 예비 후보.
- **관련 ADR·LLD:** ADR-0004, ADR-0005, LLD-0002, LLD-0006.
- **미확정 사항:** 앨범 피드가 가족 전용인지 전역 공개/프리미엄 피드인지, FCM 전달 보장 수준, 영상 실패 앨범의 사용자 노출 정책, 같은 FCM 토큰의 계정 전환 지원 정책.

### 5.5 pay

- **리뷰 상태:** 상세 리뷰 완료.
- **검토한 파일:** `Payment`, `PaymentOrder`, `PaymentCard`, `PaymentCancel`, `PaymentEasyPay`, `PaymentTransfer`, `PaymentVirtualAccount`, `PaymentStatus`, `PaymentOrderStatus`, `PointChargePackage`, `PaymentService`, `PaymentClient`, `PaymentFeignConfig`, `PaymentAuthInterceptor`, `PaymentErrorDecoder`, `PaymentLoggingInterceptor`, `PaymentRepository`, `PaymentOrderRepository`, `PaymentController`, `PaymentMapper`, 결제 DTO, `SeniorProfileService`, `SeniorProfile`, `PointHistory`, `AdminPaymentService`, `AdminPaymentResponse`, `PaymentServiceTest`, `PaymentIntegrationTest`, `PaymentControllerTest`, LLD-0003.
- **현재 도메인 경계:** pay는 포인트 충전 주문, PG 승인/취소 결과 저장, 취소 이력, 회원 포인트 반영을 하나의 Application Service가 조합한다. PG webhook/callback 코드는 pay 범위에서 발견되지 않았고, 현재 흐름은 사용자 요청 기반 주문 생성·승인·취소 REST API와 Feign PG client 중심이다.
- **Aggregate Root 후보:** `PaymentOrder`, `Payment`.
- **Aggregate 내부 Entity 후보:** `PaymentCard`, `PaymentEasyPay`, `PaymentTransfer`, `PaymentVirtualAccount`는 `Payment`에 종속된 PG 결제수단 스냅샷이다. `PaymentCancel`은 현재 `Payment`의 cancellation 컬렉션에 종속된 취소 이력으로 관리된다.
- **독립 Aggregate 후보:** `PaymentCancel`은 부분 취소가 반복·대량 증가하고 PG cancel transaction key를 가질 수 있으므로 장기적으로 독립 취소 기록 후보로 검토할 수 있다. `PointHistory`와 `SeniorProfile`은 member 포인트 Aggregate 쪽 모델이며 pay 내부 Entity가 아니다.
- **핵심 불변식:** 시니어만 포인트 충전 주문을 생성·승인한다. 생성된 주문은 15분 안에 CREATED 상태에서만 승인된다. 같은 orderId/paymentKey 승인 결과는 한 번만 Payment와 포인트 적립으로 반영된다. 취소 누적 금액은 결제 금액을 넘지 않는다. 결제 취소 시 환수 포인트는 누적 취소 비율에 맞고 포인트 잔액이 부족하면 PG 취소 전 차단한다. PG 승인·취소 완료와 내부 포인트 원장 반영은 재시도 후에도 정합성을 유지해야 한다.
- **현재 불변식 보장 위치:** 주문 소유권·시니어 검증·만료·상태 검증은 `PaymentService` 조건문과 `PaymentOrder` 메서드에 있다. 승인 멱등성은 `PaymentRepository.findByOrderId()`, `findByPaymentKey()`, `Payment.paymentKey` unique, `PaymentOrder.orderId` unique, `DataIntegrityViolationException` 처리에 분산되어 있다. 취소 누적 상태는 `Payment.cancel()`이 관리하고, 포인트 적립·차감과 PointHistory 저장은 `SeniorProfileService`가 수행한다.
- **다른 도메인과의 의존성:** pay는 `MemberUtil`로 현재 회원을 조회하고, `Member`, `MemberType`, `SeniorProfileService`, `SeniorProfile`, `PointHistory`에 의존한다. admin은 `PaymentRepository`를 조회해 결제 목록 Read Model을 만든다.
- **동기 호출 유지 대상:** 사용자 결제 승인 요청에서 PG confirm 응답 확인과 결제 금액·orderId·paymentKey 검증은 동기 처리 대상이다. 취소 전 포인트 잔액 검증도 PG 취소 호출 전에 동기적으로 필요하다.
- **이벤트 분리 후보:** 포인트 원장 반영을 결제 승인 커밋 이후 이벤트로 무조건 분리하기는 어렵다. 다만 PG 성공 후 내부 반영 실패 보정, 결제 대사, 관리자 알림은 후속 처리 후보이다.
- **Read Model 후보:** 사용자 결제 내역, 관리자 결제 목록, 결제 매출 집계, 포인트 원장 조회.
- **주요 문제:** ARCH-028~031. 특히 외부 PG 호출과 DB 트랜잭션·포인트 변경이 결합되어 외부 상태와 내부 상태의 보정 경로가 필요하다.
- **유지해야 할 구조:** `PaymentOrder`와 `Payment`를 분리한 2-step flow, `paymentKey`/`orderId` unique 기반 기본 멱등성, 취소 전 포인트 잔액 검증, `SeniorProfile.@Version`을 통한 포인트 경쟁 상태 감지.
- **개선 방향:** PG 호출과 내부 상태 반영 경계의 현재 실패 동작을 먼저 Characterization Test로 고정한다. 승인 동시 요청과 부분 취소 재전송/동시성 테스트를 보강한 뒤, PaymentOrder 상태 점유, Payment lock/version, 취소 멱등 키, PG 상태 재조회·대사 경로 중 최소 변경안을 선택한다. `PaymentClient`는 기능 변경 없이 infrastructure/client 패키지로 이동한다.
- **선행 테스트:** TEST-024~026 및 TEST-005.
- **추천 PR:** 10절의 pay 예비 후보.
- **관련 ADR·LLD:** LLD-0003.
- **미확정 사항:** Toss confirm/cancel 재호출의 실제 멱등 응답 정책, PG webhook/callback 도입 계획, 부분 취소 멱등 키 제공 방식, 결제 대사 운영 절차.

### 5.6 home/mypage/admin

- **리뷰 상태:** 상세 리뷰 완료.
- **검토한 파일:** `GuardianHomeService`, `SeniorHomeService`, `FamilyMemberQueryService`, `HomeAlbumRecommendationService`, `HomeController`, home DTO·테스트, `GuardianMyPageService`, `SeniorMyPageService`, `MyPageProfileService`, `GuardianMyPageController`, mypage DTO·테스트, `AdminDashboardService`, `AdminMemberService`, `AdminSearchService`, `AdminAlbumService`, `AdminPaymentService`, `AdminFcmStatsService`, `AdminFcmService`, `AdminPointGrantService`, `AdminAuthService`, `AdminAuditLogService`, `AdminAuditLog`, `AdminAuditLogRepository`, admin DTO·테스트, 직접 참조한 member/album/medicine/healthschedule/walk/heart/pay/fcm Repository와 `MemberRepositoryImpl`, 관련 ADR·LLD 검색 결과.
- **현재 도메인 경계:** home은 순수 도메인이 아니라 시니어/보호자 첫 화면 Read Model이다. mypage는 프로필 조회 화면과 회원·가족 변경 Command가 섞인 유스케이스 묶음이다. admin은 운영 Read Model과 관리자 Command가 섞인 backoffice 컨텍스트다.
- **Aggregate Root 후보:** home은 없음. mypage는 자체 Aggregate가 아니라 `Member`, `SeniorProfile`, `FamilyMembership`, `ParentLocation` 등 기존 Aggregate를 조작한다. admin의 자체 Aggregate 후보는 `AdminAuditLog`뿐이며, 나머지는 운영 조회/관리 유스케이스다.
- **Aggregate 내부 Entity 후보:** `AdminAuditLog`는 단독 로그 Entity다. home/mypage/admin 조회 DTO는 Aggregate 내부 Entity가 아니다.
- **독립 Aggregate 후보:** `AdminAuditLog`는 관리자 행위 감사 로그로 독립 저장 모델 후보. admin dashboard/search는 Aggregate보다 Projection/Read Model 후보가 적절하다.
- **핵심 불변식:** 보호자 홈은 연결된 시니어만 조회한다. 홈 카드 응답은 심박·약·앨범·건강일정·걷기 현황을 현재 정책대로 조합한다. 마이페이지 가족 변경은 방장만 수행하고 가족에는 최소 1명의 시니어가 남는다. 관리자 주요 작업은 일관된 감사 로그 정책을 따른다.
- **현재 불변식 보장 위치:** home의 접근 제어는 Controller `@ValidateFamilyAccess`와 `GuardianHomeService.resolveSenior()` 조건문에 중복되어 있다. mypage의 가족 접근·방장 검증은 `GuardianMyPageService` private 메서드와 Repository exists 쿼리에 있다. admin 권한은 security/controller 밖에서 일부 Service 조건문으로 보강되며, 감사 로그는 `AdminAuditLogService` 또는 `AdminAuthService` 직접 저장으로 분산되어 있다.
- **다른 도메인과의 의존성:** home은 member/family, heart Redis, medicine, medication proof, healthschedule, walk, album Repository를 직접 조합한다. mypage는 member/family, auth Redis, SMS, S3, geocoding, parentlocation, pointHistory를 조합한다. admin은 member/family, album, payment, heart, fcm, auth, audit log를 조합한다.
- **동기 호출 유지 대상:** 홈 카드 조회의 가족 접근 검증, mypage 프로필·가족 변경의 방장 검증, admin 회원 상태 변경과 포인트 지급 명령.
- **이벤트 분리 후보:** admin 감사 로그는 정책 확정 후 성공 로그/시도 로그 기준으로 통일한다. mypage 주소 변경 후 HOME ParentLocation 갱신은 같은 강한 일관성이 필요한지 확인 후 별도 유스케이스 위임 또는 후속 처리 후보로 검토한다.
- **Read Model 후보:** 보호자/시니어 홈 카드, 가족 시니어 목록, 마이페이지 프로필/가족 구성원/포인트 내역/비상연락처, admin dashboard, admin member detail, admin search, admin FCM stats, admin payment/album pages.
- **주요 문제:** ARCH-005, ARCH-032~034. 기존 ARCH-004/011의 가족 접근 정책 분산도 home/mypage에서 재확인됐다.
- **유지해야 할 구조:** home과 admin을 도메인 Aggregate로 승격하지 않고 query/read model 경계로 관리한다. mypage의 가족 변경 정책은 member/family 불변식과 맞춰 유지한다. AdminAuditLog는 삭제하지 않고 운영 감사 저장 모델로 유지한다.
- **개선 방향:** home/admin 조회 응답과 쿼리 수를 Characterization Test로 고정한 뒤 전용 Query Repository/Projection을 도입한다. mypage는 조회 Service와 Command Service를 분리하고 가족 접근·리더 정책을 member/family 정책 API로 모은다. admin 감사 로그는 성공/시도/실패 기록 의미와 트랜잭션 전파를 결정한다.
- **선행 테스트:** TEST-027~030.
- **추천 PR:** 10절의 home/mypage/admin 예비 후보.
- **관련 ADR·LLD:** home/mypage/admin 전용 문서 없음. LLD-0007, LLD-0008, LLD-0003, LLD-0002와 일부 조회 응답이 간접 관련.
- **미확정 사항:** 홈/admin 조회 허용 쿼리 수, 관리자 화면 호출 빈도, 마이페이지 주소 변경과 HOME 안전구역의 강한 일관성 필요 여부, 감사 로그 실패 시도 기록 필요 여부.

## 6. 테스트 보강 계획

| ID | 테스트 대상 | 보호할 동작 | 테스트 유형 | 우선순위 | 상태 | 이후 가능한 리팩터링 |
| -- | ------ | ------ | ------ | ---- | -- | ----------- |
| TEST-001 | Refresh Token 재발급 | 최신 토큰만 유효, 재발급 저장 1회 | Redis 통합 테스트 | P0 | 완료 | Refresh Token rotation 단순화 |
| TEST-002 | SeniorAuthService | guardian만 Family·Senior 등록 가능 | Service 단위 테스트 | P0 | 작업 예정 | 가입 권한 정책 명시 |
| TEST-003 | MemberWithdrawService + Membership | 탈퇴 guardian이 유효 leader로 남지 않음 | JPA 통합 테스트 | P0 | 작업 예정 | Membership 생명주기 정리 |
| TEST-004 | FamilyAccessService/Aspect | AOP와 Service 경로의 권한 판정 일치 | 단위 + Controller 통합 테스트 | P1 | 작업 예정 | 정책 추출·패키지 이동 |
| TEST-005 | 모듈·계층 규칙 | Entity/Repository/Controller 경계 유지 | 아키텍처 경계 테스트 | P1 | 작업 예정 | 모듈 경계 리팩터링 |
| TEST-006 | Senior 가입 지오코딩 실패 | 실패 시 현재 가입 원자성 보존 | Characterization + 외부 연동 실패 테스트 | P1 | 작업 예정 | 외부 호출 경계 분리 |
| TEST-007 | FamilyMembership 동시 참여 | guardian 한 Family 및 leader 규칙 보존 | Repository/JPA 동시성 테스트 | P1 | 작업 예정 | Membership 정책 변경 |
| TEST-008 | RealtimeLocationService | 최신 위치·trail·stay 갱신과 전달 순서 | Characterization Test | P1 | 작업 예정 | 위치 상태 갱신 책임 분리 |
| TEST-009 | ParentLocation CUD | 연결 보호자만 시니어 안전구역 변경 | Controller + Service 통합 테스트 | P0 | 작업 예정 | 가족 접근 정책 재사용 |
| TEST-010 | STOMP location·heart 구독 | 본인·가족 외 topic 구독 거부 | WebSocket 보안 통합 테스트 | P0 | 작업 예정 | 구독 인가 interceptor |
| TEST-011 | 안전구역 이탈 | 병렬 이탈 요청도 30분 내 이벤트·FCM 한 번 | Redis 동시성 테스트 | P1 | 작업 예정 | 안전구역 알림 후속 처리 분리 |
| TEST-012 | 심박 수집 재처리 | 동일 배치의 Event·Emergency·알림 중복 없음 | JPA/Redis 통합 + 동시성 테스트 | P0 | 작업 예정 | 심박 수집 멱등성 |
| TEST-013 | 위치·심박 외부 실패 | FCM·WebSocket·AI 실패와 저장 상태의 경계 | Characterization + 외부 연동 실패 테스트 | P1 | 작업 예정 | 후속 효과·AI 트랜잭션 분리 |
| TEST-014 | HealthScheduleRewardService | 완료·미완료·이미 보상받은 일정의 포인트 지급 기준 | Characterization + Service 테스트 | P0 | 작업 예정 | 건강 일정 보상 정책 구현 |
| TEST-015 | MedicationProofService | 같은 회원·스케줄·날짜 인증은 한 번만 저장되고 S3 실패 시 DB 기록 없음 | JPA 통합 + 동시성 + 외부 실패 테스트 | P0 | 작업 예정 | 복약 인증 멱등성·파일 업로드 경계 분리 |
| TEST-016 | MedicineScheduleRewardScheduler | 같은 회원·날짜 복약 보상은 재실행되어도 한 번만 적립 | Scheduler Characterization + JPA 통합 테스트 | P0 | 작업 예정 | 복약 정산 원장 도입 |
| TEST-017 | WalkService.updateSteps | 목표 달성 보상은 한 번만 지급되고 PointHistory 정책이 일관됨 | Service + 동시성 테스트 | P1 | 작업 예정 | 걷기 보상 경계 정리 |
| TEST-018 | GoalHomeService | 약·걷기·건강일정 조합 조회의 현재 응답 의미 보존 | Characterization Test | P2 | 작업 예정 | 건강관리 Read Model 분리 |
| TEST-019 | Album Like/View/Unlock | 중복·동시 요청에서도 row, 카운터, 포인트 차감이 한 번만 반영 | JPA 통합 + 동시성 테스트 | P0 | 작업 예정 | 앨범 상호작용 Aggregate 경계 정리 |
| TEST-020 | AlbumPermissionService/AlbumFeedService | 가족 범위 또는 전역 피드 접근 정책의 현재 동작 보존 | Characterization + Controller 통합 테스트 | P0 | 작업 예정 | 앨범 접근 정책 변경 |
| TEST-021 | AlbumVideoProcessingService | 영상 처리 실패·부분 S3 업로드 실패 시 앨범 상태와 파일 정리 정책 보존 | 비동기 + 외부 실패 테스트 | P1 | 작업 예정 | 영상 처리 실패 상태·재시도 모델 도입 |
| TEST-022 | AlbumNotificationListener/FcmService | FCM 전송 실패가 앨범 생성·좋아요·댓글·잠금 해제 트랜잭션을 롤백하지 않음 | Characterization + 이벤트 테스트 | P0 | 작업 예정 | AFTER_COMMIT 알림 분리 |
| TEST-023 | MemberFcmTokenService | 같은 토큰이 다른 회원으로 등록될 때 현재 회원에게 활성 귀속됨 | Service + Repository 통합 테스트 | P1 | 작업 예정 | FCM 토큰 소유권 이전 정책 정리 |
| TEST-024 | PaymentService confirm/cancel | PG 성공 후 내부 저장·포인트 반영 실패 시 현재 응답과 재시도 동작 보존 | Characterization + 외부 실패 테스트 | P0 | 작업 예정 | 결제 보정 경로 설계 |
| TEST-025 | PaymentService.confirmPayment | 동시 승인 요청에서도 PG confirm 중복 호출과 내부 Payment/PointHistory 중복 반영 여부 확인 | JPA 통합 + 동시성 테스트 | P0 | 작업 예정 | 승인 멱등성 보강 |
| TEST-026 | PaymentService.cancelPayment | 부분 취소 재전송·동시 취소에서 PaymentCancel, canceledAmount, 포인트 차감이 중복되지 않음 | JPA 통합 + 동시성 테스트 | P0 | 작업 예정 | 취소 멱등 키·lock 도입 |
| TEST-027 | GuardianHomeService/SeniorHomeService | 홈 카드 응답 조합, 가족 접근, null/empty 정책과 주요 쿼리 수 보존 | Characterization + Repository 통합 테스트 | P1 | 작업 예정 | home Read Model/Projection 분리 |
| TEST-028 | GuardianMyPageService/SeniorMyPageService | 조회/명령 응답과 방장·가족 변경 불변식, S3·지오코딩 실패 정책 보존 | Service + 외부 실패 + JPA 통합 테스트 | P1 | 작업 예정 | mypage 조회·명령 분리 |
| TEST-029 | AdminDashboard/Search/Detail | 관리자 대시보드·검색·상세 응답과 쿼리 수/Projection 계약 보존 | Repository 통합 + 성능 Characterization Test | P1 | 작업 예정 | admin Query Repository/Read Model 분리 |
| TEST-030 | AdminAuditLogService | 관리자 로그인·상태변경·FCM 테스트의 감사 로그 기록 시점과 롤백 정책 보존 | Service + 트랜잭션 통합 테스트 | P2 | 작업 예정 | 감사 로그 트랜잭션 정책 통일 |

## 7. ADR·LLD 정합성

| 문서 | 관련 코드 | 상태 | 불일치 내용 | 필요한 조치 |
| -- | ----- | -- | ------ | ------ |
| [ADR-0002](../adr/ADR-0002-auth-jwt-family-access.md) | `JwtTokenProvider`, `FamilyAccessAspect`, `GuardianAuthController` | 일부 불일치 | ADR은 Refresh Token을 HttpOnly Secure 쿠키로 설명하나 확인한 Controller는 TokenPair를 응답 본문으로 반환한다. 또한 가족 접근 정책은 Aspect 외 Service에도 분산된다. | 쿠키 전달 구현 유무 확인, 권한 정책 단일화 후 ADR 갱신 |
| [LLD-0004](../lld/LLD-0004-apple-login.md) | `SocialLoginService`, `SocialAccount`, `MemberWithdrawService` | 일치 | Apple id_token 서명·claim 검증 미구현 위험이 LLD에 명시되어 있다. | 보안 개선 시 별도 ADR/LLD 작성 |
| [ADR-0007](../adr/ADR-0007-location-event-visit-verification.md) | `RealtimeLocationService`, `HealthScheduleLocationEventListener` | 일치 | 위치 이벤트로 건강 일정 자동 완료를 비동기 처리한다는 결정은 구현과 일치한다. 영속 이벤트·재시도 제외도 문서에 명시돼 있다. | healthschedule 상세 리뷰에서 리스너 멱등성과 실패 관측을 추가 확인 |
| [LLD-0001](../lld/LLD-0001-websocket-realtime-location.md) | location Redis·WebSocket·safezone 코드 | 일부 불일치 | 최신 위치·trail·stay 분리는 일치한다. 다만 30분 중복 방지는 `hasKey` 후 `set`으로 원자성이 없고, topic 구독 가족 인가는 설계에 명시되지 않았다. | TEST-010·011 후 구독 인가와 원자 중복 차단을 LLD에 반영 |
| [LLD-0009](../lld/LLD-0009-location-based-health-schedule-verification.md) | `SeniorLocationUpdatedEvent`, `HealthScheduleLocationEventListener` | 일치 | 위치 저장 후 비동기 리스너가 방문 일정 완료를 위임하는 흐름이 일치한다. | healthschedule 상세 리뷰에서 시간창·반경·재시도 검증 |
| [LLD-0003](../lld/LLD-0003-payment-points.md) | `PaymentService`, `PaymentOrder`, `Payment`, `PaymentCancel`, `SeniorProfileService`, `WalkService`, `HealthScheduleRewardService`, `MedicineScheduleRewardScheduler` | 일부 불일치 | 결제 2-step flow, Payment/PaymentOrder 상태, 취소 비례 포인트 환수, 포인트 낙관적 락 미재시도 경로는 구현과 일치한다. 다만 승인 요청 DTO 예시는 `amount`를 포함하지만 현재 `PaymentApproveRequest`는 `orderId`, `paymentKey`만 받는다. 또한 걷기 보상은 `SeniorProfile.addPoints()`를 직접 호출하고 건강 일정 보상은 실제 포인트 적립이 구현되지 않았다. | ARCH-019와 TEST-014·017로 건강관리 보상 정책 결정, ARCH-028~030과 TEST-024~026으로 결제 멱등성·보정 경로 보강 |
| [ADR-0006](../adr/ADR-0006-medicine-search-fallback-fulltext.md) | `ExternalMedicineService`, `MedicineRepository`, `MedicineSyncScheduler` | 일치 | DB 우선 조회, 외부 API fallback, itemSeq 중복 처리, 월 1회 배치 동기화 흐름이 문서와 일치한다. | 약품 삭제·수정 동기화 정책은 문서의 후속 과제로 유지 |
| [LLD-0005](../lld/LLD-0005-medicine-search-fallback.md) | `ExternalMedicineService`, `MedicineRepository`, `MedicineSyncScheduler` | 일치 | 검색 실패 시 빈 목록 반환, FULLTEXT/prefix 조회, 외부 API fallback, upsert 흐름이 일치한다. | 트래픽 증가 시 서킷 브레이커·분산 락 재검토 |
| [LLD-0007](../lld/LLD-0007-medicine-daily-status.md) | `MedicineScheduleService`, `MedicationProofService`, `MedicationStatus` | 일부 불일치 | 일자별 상태 조회와 인증 허용창 공유는 일치한다. 다만 복약 인증 중복 방지가 선조회에 의존하고 DB 제약이 없어 LLD의 "기존 유지" 영역이 동시성 위험을 남긴다. | TEST-015 후 인증 멱등성 LLD 개정 여부 결정 |
| [LLD-0008](../lld/LLD-0008-medicine-schedule-versioning.md) | `MedicineSchedule`, `MedicineScheduleService`, `MedicineScheduleRepository`, `MedicineScheduleRewardScheduler` | 일부 불일치 | 유효기간 버전링, 과거 보존, 일자별/월별 조회, 알림·정산의 유효 스케줄 기준은 일치한다. 다만 복약 정산 재실행 멱등성은 문서와 구현 모두에서 별도 모델이 없다. | ARCH-020과 TEST-016 후 reward settlement 설계 추가 |
| [LLD-0002](../lld/LLD-0002-fcm-notification.md) | `HeartRateService`, `HeartMessageService`, `AlbumNotificationListener`, `FcmService`, `MemberFcmTokenService` | 일부 불일치 | 심박수 이상 감지 알림을 기재하지만 확인한 FCM 호출은 수동 가족 메시지뿐이다. 앨범 알림의 동기 `@EventListener`와 RestTemplate 런타임 예외 전파 가능성은 문서의 리스크와 일치하지만 아직 격리되지 않았다. | 제품 정책 결정 후 심박 알림 문서 또는 구현 정합화, 앨범 알림 실패 격리 정책 추가 |
| [ADR-0004](../adr/ADR-0004-media-upload-strategy.md) | `AlbumFileService`, `AlbumVideoProcessingService`, `S3ServiceImpl` | 일치 | 이미지 즉시 업로드, 영상 임시 파일 + 비동기 처리, 처리 완료 후 ACTIVE 전환 흐름이 구현과 대체로 일치한다. | 실패 재시도·부분 업로드 정리 정책을 결정하면 ADR 또는 LLD 개정 |
| [ADR-0005](../adr/ADR-0005-cursor-pagination.md) | `AlbumFeedService`, `AlbumRepository` | 일치 | cursor 조회에서 id 선조회 후 collection fetch를 수행하는 전략이 구현과 일치한다. | 피드 접근 범위가 가족 범위로 바뀌면 필터 정책 추가 |
| [LLD-0006](../lld/LLD-0006-album-video-upload-pipeline.md) | `AlbumFacadeImpl`, `AlbumService`, `AlbumVideoProcessingService`, `AlbumFileService` | 일부 불일치 | 202 응답, PROCESSING 저장, 비동기 영상 처리, 실패 시 DELETED 처리는 일치한다. 다만 문서에서 범위 밖으로 둔 큐·재시도·재시작 복구·실패 노출 정책이 운영 리스크로 남아 있다. | ARCH-025와 TEST-021 후 실패 상태·재시도·S3 보상 삭제 정책 추가 |
| home/mypage/admin 전용 문서 | `GuardianHomeService`, `SeniorHomeService`, `GuardianMyPageService`, `AdminDashboardService`, `AdminMemberService`, `AdminSearchService` | 문서 없음 | 화면·운영 조회 경계, Projection 기준, 관리자 감사 로그 정책을 설명하는 ADR·LLD가 없다. | ARCH-005, ARCH-032~034 결정 후 필요 시 조회 경계 ADR 또는 간단 LLD 작성 |

### 7.1 새 ADR 후보

- Refresh Token rotation 및 단일 세션/다중 세션 정책.
- FamilyMembership 탈퇴·leader 승계·보존 정책.
- 순수 domain model과 persistence/Redis adapter의 장기 분리 기준.
- WebSocket 가족 범위 topic 구독 인가 정책.
- 심박 이상 알림·재전송 멱등성 정책.
- 건강관리 보상 정책과 reward ledger 도입 기준.
- `goal` 패키지를 화면 컨텍스트로 볼지 건강관리 상위 컨텍스트로 볼지에 대한 명명·경계 결정.
- 앨범 피드 공개 범위와 가족 접근 정책.
- 앨범 알림 이벤트 소유권과 FCM 전달 보장 수준.
- PG 승인·취소와 내부 포인트 반영의 실패 보정 및 대사 정책.
- home/admin 조회 경계와 Read Model/Projection 도입 기준.
- AdminAuditLog의 성공·시도·실패 기록 및 트랜잭션 전파 정책.

### 7.2 수정할 ADR

- ADR-0002: Refresh Token 전달 방식, 가족 접근 정책의 실제 적용 경계를 확인 후 수정.
- ADR-0007: 안전구역 FCM 전달 보장 수준을 변경하기로 결정할 때 수정.

### 7.3 수정할 LLD

- LLD-0004: 현재는 수정 필요 없음. Apple JWKS 검증을 구현할 때 후속 LLD 또는 개정 필요.
- LLD-0001: STOMP 구독 인가와 원자적 안전구역 알림 중복 차단을 구현할 때 수정.
- LLD-0002: 심박 이상 FCM의 실제 요구사항을 결정한 뒤 수정.
- LLD-0007/0008: 복약 인증·정산 멱등성 정책을 구현할 때 수정.
- LLD-0009: 건강 일정 실제 포인트 지급 정책을 도입할 때 수정.
- LLD-0006: 영상 처리 실패 상태, 재시도, S3 부분 업로드 정리 정책을 구현할 때 수정.
- LLD-0003: 결제 승인 DTO 예시와 현재 요청 필드 정합화, PG 호출 중복 방지·부분 취소 멱등성 정책을 구현할 때 수정.

### 7.4 Deprecated 처리 후보

- 현재 없음. 판단 전 기존 문서를 삭제하거나 Deprecated 처리하지 않는다.

### 7.5 Critical·High 이슈 정합성 리뷰 결과

- **읽은 ADR·LLD:** ADR-0002, ADR-0004, ADR-0007, LLD-0001, LLD-0002, LLD-0003, LLD-0006, LLD-0007, LLD-0008, LLD-0009.
- **읽은 테스트:** `GuardianTokenServiceTest`, `JwtTokenProviderTest`, `FamilyAccessAspectTest`, `ParentLocationServiceTest`, `RealtimeLocationServiceTest`, `WsTokenServiceRedisTest`, `HeartRateServiceTest`, `HeartRateAnomalyDetectorTest`, `HealthScheduleLocationEventListenerTest`, `MedicationProofServiceTest`, `MedicineScheduleRewardSchedulerTest`, `WalkServiceTest`, `AlbumLikeServiceTest`, `AlbumNotificationListenerTest`, `PaymentServiceTest`, `PaymentIntegrationTest` 및 관련 테스트 파일명 목록.
- **인증·가족 접근:** ADR-0002는 `@ValidateFamilyAccess` 중심 정책을 설명하지만 현재 테스트는 Aspect 단위 예외만 검증한다. AOP와 Service 수동 검증의 동일성, ParentLocation CUD 인가, Refresh Token Redis 회전 값 검증은 TEST-001, TEST-004, TEST-009로 보강해야 한다.
- **WebSocket·위치·심박:** LLD-0001은 GETDEL 일회성 토큰을 명시하고 `WsTokenServiceRedisTest`가 이를 통합 테스트로 검증한다. 반면 location/heart topic `SUBSCRIBE` 가족 인가와 안전구역 중복 알림 원자성은 테스트가 없어 TEST-010, TEST-011이 리팩터링 전 필수다. LLD-0002의 심박 이상 FCM은 구현·테스트 모두 수동 하트 메시지 FCM과 구분되지 않아 ARCH-018 결정 전 확인 필요다.
- **건강관리 상태 전이·보상:** LLD-0007/0008은 복약 상태 조회와 스케줄 버전링을 설명하지만, `MedicationProofServiceTest`와 `MedicineScheduleRewardSchedulerTest`는 정상/단일 조건 위주다. 중복 인증, 정산 재실행, 포인트 지급 멱등성은 TEST-014~017 전까지 보호되지 않는다. LLD-0009는 위치 이벤트 기반 방문인증과 일치하지만 실제 포인트 지급은 범위 밖으로 문서화되어 ARCH-019와 별도 결정이 필요하다.
- **앨범·FCM:** ADR-0004와 LLD-0006은 영상 비동기 처리의 현재 성공/실패 흐름과 대체로 일치한다. 다만 큐·재시도·재시작 복구는 문서상 범위 밖이고, `AlbumNotificationListenerTest`는 예외 경로만 확인해 FCM 실패가 앨범 트랜잭션을 롤백하지 않는지는 TEST-022로 고정해야 한다.
- **결제:** LLD-0003은 현재처럼 PG 호출과 포인트 반영을 한 트랜잭션 유스케이스로 설명하지만, 외부 PG 성공 뒤 내부 저장 실패·동시 승인·부분 취소 재전송은 `PaymentServiceTest`와 `PaymentIntegrationTest`에서 검증되지 않는다. TEST-024~026이 결제 Aggregate 경계 변경 전 P0 게이트다.
- **home/mypage/admin:** 전용 ADR·LLD는 문서 없음 상태가 맞다. 이 영역은 새 도메인 문서보다 TEST-027~030으로 조회 응답, 쿼리 수, 감사 로그 트랜잭션 정책을 먼저 고정한 뒤 Read Model/Projection 기준 문서를 작성한다.

## 8. 목표 구조

### 8.1 단기 목표 구조

- 현재 `widyu-api`, `widyu-domain` 두 모듈을 유지한다.
- `member` 하위에 현재 Member 조회와 가족 접근 Application 정책을 둔다.
- `global`은 SecurityContext, HTTP 응답, 공통 예외 처리처럼 도메인 비의존 코드로 제한한다.
- home/mypage/admin은 쓰기 도메인 Service와 구분되는 조회 경계를 명시한다.
- 위치·외부 연동은 유스케이스와 저장·브로드캐스트·클라이언트 책임을 패키지 수준에서 분리한다.
- 위치·심박 WebSocket 구독 인가는 transport adapter에 두되 가족 관계 판정은 `member/family` Application 정책을 재사용한다.
- Redis 최신 상태·이력 Read Model과 JPA 이력 Aggregate를 병합하지 않고, 멱등성 키와 후속 효과 경계를 명시한다.
- 건강관리 보상은 기능별 Service가 직접 포인트를 변경하지 않고 공통 포인트 지급 정책 또는 reward ledger를 통해 멱등하게 처리한다.
- `goal/home`은 건강관리 쓰기 도메인과 구분되는 조회 조합 Read Model로 관리한다.
- 앨범은 콘텐츠·미디어 처리 상태와 대량 상호작용 기록의 경계를 구분하고, 알림 전송은 앨범 쓰기 트랜잭션의 후속 처리로 격리한다.
- FCM 토큰·알림 저장 모델은 회원 도메인 Aggregate가 아니라 알림 인프라 저장 모델로 관리하되, 회원-토큰 활성 귀속 불변식은 테스트로 보호한다.
- 결제는 `PaymentOrder`와 `Payment`의 상태 전이를 유지하되, PG 외부 호출과 내부 포인트 반영 실패를 보정할 수 있는 멱등·대사 경계를 명시한다.
- home/admin은 도메인 Aggregate가 아니라 조회 조합 Read Model로 관리하고, mypage는 조회와 회원·가족 변경 Command를 분리한다.

### 8.2 장기 검토 구조

> 아래는 확정안이 아닌 검토안이다. 모듈 수 증가의 운영 비용을 별도 평가한다.

```text
domain-model        # 기술 비의존 Aggregate, Value Object, 도메인 정책
application          # Use Case, 트랜잭션, 포트
persistence-adapter  # JPA Entity/Repository/QueryDSL
redis-adapter        # RedisHash, TTL, Redis Repository
external-client      # OAuth, FCM, S3, PG, Geocoding
web-api              # REST, WebSocket, Security/AOP
query-read-model     # home/mypage/admin 조합 조회
```

## 9. 구조 개선 로드맵

### 9.1 1단계: 리스크 낮은 구조 정리

- **작업 ID:** TASK-001
- **작업:** Refresh Token rotation 검증 수정
- **해결할 이슈:** ARCH-008
- **대상 경로:** `auth`, `global/security`, `auth/RefreshToken`
- **선행 테스트:** TEST-001
- **변경 위험:** 인증 세션 재발급 회귀
- **완료 조건:** 이전 토큰 재사용이 거부되고 재발급 저장이 한 번만 수행됨
- **롤백 가능성:** 높음. 저장·검증 로직과 테스트를 한 PR로 되돌릴 수 있음
- **추천 PR:** `fix(auth): validate rotated refresh token value`
- **상태:** 완료 (2026-07-15). 이슈 #395, 브랜치 fix/#395. 완료 조건 충족 — 이전 토큰 재사용 거부와 재발급 저장 1회를 단위·Redis 통합 테스트로 검증. 독립 리뷰 APPROVE.

- **작업 ID:** TASK-002
- **작업:** Senior 가입 행위자 타입 검증
- **해결할 이슈:** ARCH-009
- **대상 경로:** `auth/application/senior`
- **선행 테스트:** TEST-002
- **변경 위험:** 기존 클라이언트의 부적절한 호출 차단
- **완료 조건:** guardian 외 요청이 일관된 FORBIDDEN으로 거부됨
- **롤백 가능성:** 높음
- **추천 PR:** `fix(auth): restrict senior provisioning to guardians`

- **작업 ID:** TASK-007
- **작업:** ParentLocation CUD 가족 소유권 검증
- **해결할 이슈:** ARCH-013, ARCH-011
- **대상 경로:** `location/parentlocation`, `member/family`
- **선행 테스트:** TEST-009, TEST-004
- **변경 위험:** 기존에 허용되던 비정상 호출이 거부됨
- **완료 조건:** 비가족·guardian 대상 변경은 거부되고 연결 보호자만 변경 가능함
- **롤백 가능성:** 높음
- **추천 PR:** `fix(location): authorize parent location mutations`

- **작업 ID:** TASK-008
- **작업:** 가족 범위 STOMP topic 구독 인가
- **해결할 이슈:** ARCH-014, ARCH-011
- **대상 경로:** `global/websocket`, `location/realtime`, `heart`
- **선행 테스트:** TEST-010, TEST-004
- **변경 위험:** 기존 WebSocket 클라이언트의 구독 흐름 차단 가능성
- **완료 조건:** 본인·연결 보호자 외 `/topic/location/senior/{id}`, `/topic/heart-rate/{id}` 구독이 거부됨
- **롤백 가능성:** 중간
- **추천 PR:** `fix(websocket): authorize family-scoped subscriptions`

- **작업 ID:** TASK-021
- **작업:** FCM 토큰 소유권 이전 정책 정리
- **해결할 이슈:** ARCH-027
- **대상 경로:** `fcm/application/MemberFcmTokenService`, `fcm/MemberFcmToken`
- **선행 테스트:** TEST-023
- **변경 위험:** 기기 토큰 재등록·로그인 전환 시 알림 수신 동작 변화
- **완료 조건:** 같은 토큰을 다른 회원이 등록해도 현재 회원의 활성 토큰으로 귀속되고 기존 회원의 활성 토큰은 남지 않음
- **롤백 가능성:** 중간. 토큰 unique 제약 유지 여부에 따라 달라짐
- **추천 PR:** `fix(fcm): handle token ownership transfer`

- **작업 ID:** TASK-025
- **작업:** PaymentClient 패키지 경계 정리
- **해결할 이슈:** ARCH-031
- **대상 경로:** `pay/config/PaymentClient`, `pay/config/PaymentFeignConfig`, `pay/infrastructure` 후보
- **선행 테스트:** TEST-005
- **변경 위험:** import 경로 변경으로 인한 컴파일 회귀
- **완료 조건:** 외부 PG Client 계약은 infrastructure/client 패키지에 있고 Feign 설정만 config에 남음
- **롤백 가능성:** 높음
- **추천 PR:** `refactor(pay): move payment client to infrastructure package`

### 9.2 2단계: 조회와 유스케이스 경계 정리

- **작업 ID:** TASK-003
- **작업:** home/mypage/admin 상세 리뷰 후 조회 경계 설계
- **해결할 이슈:** ARCH-005
- **대상 경로:** `home`, `mypage`, `admin`
- **선행 테스트:** 해당 화면 Characterization Test
- **변경 위험:** API 응답·권한 필터 회귀
- **완료 조건:** 화면별 Repository 조합 책임이 문서화되고 최소 한 영역의 경계가 확정됨
- **롤백 가능성:** 중간
- **추천 PR:** 예비 후보 `refactor(home): isolate dashboard query composition`

- **작업 ID:** TASK-016
- **작업:** GoalHome 건강관리 조회 경계 Characterization Test 및 Read Model 후보 정리
- **해결할 이슈:** ARCH-022, ARCH-005
- **대상 경로:** `goal/home`, `goal/medicineschedule`, `goal/walk`, `goal/healthschedule`
- **선행 테스트:** TEST-018
- **변경 위험:** 목표 홈 응답 계산 회귀
- **완료 조건:** 약·걷기·건강일정 조합 조회의 현재 의미가 테스트로 고정되고 쓰기 유스케이스와 조회 조합 책임이 문서화됨
- **롤백 가능성:** 높음
- **추천 PR:** `test(goal): characterize health dashboard query composition`

- **작업 ID:** TASK-018
- **작업:** Album 접근 정책 Characterization Test 및 공개 범위 결정
- **해결할 이슈:** ARCH-024
- **대상 경로:** `album/application/AlbumPermissionService`, `album/application/AlbumFeedService`, `album/application/AlbumUnlockService`
- **선행 테스트:** TEST-020
- **변경 위험:** 기존 앨범 피드·상세 조회 접근 범위 변화
- **완료 조건:** 앨범 피드가 가족 범위인지 전역 공개/프리미엄 피드인지 테스트와 문서로 확정됨
- **롤백 가능성:** 중간
- **추천 PR:** `test(album): characterize album visibility policy`

- **작업 ID:** TASK-026
- **작업:** Home 카드 조회 Characterization Test 및 Read Model 후보 정리
- **해결할 이슈:** ARCH-005
- **대상 경로:** `home/application`, `home/dto`, 관련 member/goal/heart/album Repository
- **선행 테스트:** TEST-027
- **변경 위험:** 홈 카드 응답의 null/empty 정책과 가족 접근 필터 회귀
- **완료 조건:** 시니어/보호자 홈 카드의 현재 조합 의미와 주요 쿼리 수가 테스트로 고정됨
- **롤백 가능성:** 높음
- **추천 PR:** `test(home): characterize dashboard query composition`

- **작업 ID:** TASK-028
- **작업:** Admin dashboard/search/member detail 조회 경계 정리
- **해결할 이슈:** ARCH-033, ARCH-005
- **대상 경로:** `admin/application/AdminDashboardService`, `AdminSearchService`, `AdminMemberService`, admin DTO, 관련 Repository
- **선행 테스트:** TEST-029
- **변경 위험:** 관리자 집계·검색 응답 의미와 정렬 기준 회귀
- **완료 조건:** 관리자 조회 응답과 쿼리 수가 고정되고 Projection Query Repository 후보가 문서화됨
- **롤백 가능성:** 높음. Projection 도입 전 테스트 PR은 되돌리기 쉬움
- **추천 PR:** `test(admin): characterize dashboard and search queries`

### 9.3 3단계: Aggregate와 불변식 정리

- **작업 ID:** TASK-004
- **작업:** FamilyMembership 탈퇴·leader 정책 결정 및 구현
- **해결할 이슈:** ARCH-010
- **대상 경로:** `member`, `auth/application/guardian`
- **선행 테스트:** TEST-003, TEST-007
- **변경 위험:** 기존 가족 접근·탈퇴 데이터 정책 변화
- **완료 조건:** Membership 생명주기와 leader 승계 정책이 테스트·ADR로 명확함
- **롤백 가능성:** 정책·데이터 마이그레이션 여부에 따라 낮아질 수 있음
- **추천 PR:** `refactor(member): define membership withdrawal lifecycle`

- **작업 ID:** TASK-009
- **작업:** 심박 수집 배치 멱등성 설계 및 구현
- **해결할 이슈:** ARCH-016
- **대상 경로:** `heart`, 관련 DB 마이그레이션, WebSocket DTO
- **선행 테스트:** TEST-012
- **변경 위험:** 장치·클라이언트 수집 계약 및 기존 응급기록 조회 영향
- **완료 조건:** 동일 배치 재처리·동시 처리에도 Event·Emergency가 한 번만 기록됨
- **롤백 가능성:** 멱등 키 스키마 마이그레이션 전에는 높음
- **추천 PR:** `refactor(heart): make heart-rate ingestion idempotent`

- **작업 ID:** TASK-013
- **작업:** 건강관리 보상 정책과 포인트 원장 경계 정리
- **해결할 이슈:** ARCH-019
- **대상 경로:** `goal/healthschedule`, `goal/walk`, `goal/medicineschedule`, `member/application/SeniorProfileService`
- **선행 테스트:** TEST-014, TEST-017
- **변경 위험:** 기존 보상 지급 시점과 포인트 잔액 변화
- **완료 조건:** 건강 일정·걷기·복약 보상이 공통 정책으로 포인트와 원장 기록을 일관되게 남기며 중복 지급이 차단됨
- **롤백 가능성:** 중간. 원장 스키마 변경 여부에 따라 달라짐
- **추천 PR:** `refactor(goal): centralize health reward point policy`

- **작업 ID:** TASK-014
- **작업:** 복약 인증 멱등성 및 파일 업로드 경계 정리
- **해결할 이슈:** ARCH-021
- **대상 경로:** `goal/medicineschedule/application/MedicationProofService`, `medicine/MedicationProof`, S3 연동
- **선행 테스트:** TEST-015
- **변경 위험:** 인증 API와 proof 이미지 저장 정책 변경
- **완료 조건:** 회원·스케줄·날짜 기준 중복 인증이 DB 수준에서 차단되고 S3 실패 시 DB 기록이 남지 않음
- **롤백 가능성:** 스키마 변경 전 높음, unique 제약 반영 후 중간
- **추천 PR:** `fix(medicine): enforce medication proof idempotency`

- **작업 ID:** TASK-015
- **작업:** 복약 포인트 정산 멱등성 설계 및 구현
- **해결할 이슈:** ARCH-020
- **대상 경로:** `goal/medicineschedule/scheduler`, `member point`, 정산 원장 후보
- **선행 테스트:** TEST-016
- **변경 위험:** 배치 재실행 시 포인트 지급 정책 변화
- **완료 조건:** 같은 회원·날짜 복약 보상은 Scheduler 재실행 또는 다중 실행에도 한 번만 적립됨
- **롤백 가능성:** 원장 스키마 변경 전 높음, 반영 후 중간
- **추천 PR:** `fix(medicine): make medication reward settlement idempotent`

- **작업 ID:** TASK-017
- **작업:** Album 상호작용 멱등성 및 카운터 정책 검증
- **해결할 이슈:** ARCH-023
- **대상 경로:** `album/application/AlbumLikeService`, `album/application/AlbumViewService`, `album/application/AlbumUnlockService`, `album/Album`
- **선행 테스트:** TEST-019
- **변경 위험:** 좋아요·조회·잠금 해제 카운터와 포인트 차감 정책 회귀
- **완료 조건:** 중복·동시 요청에서 row, 카운터, 포인트 차감이 현재 정책대로 한 번만 반영됨
- **롤백 가능성:** 높음. 스키마 변경 전 테스트와 Service 로직 변경을 분리 가능
- **추천 PR:** `test(album): characterize interaction idempotency and counters`

- **작업 ID:** TASK-023
- **작업:** 결제 승인 멱등성 보강
- **해결할 이슈:** ARCH-029
- **대상 경로:** `pay/application/PaymentService`, `pay/PaymentOrder`, `pay/Payment`
- **선행 테스트:** TEST-025
- **변경 위험:** 결제 승인 재시도와 동시 요청 처리 방식 변화
- **완료 조건:** 같은 orderId/paymentKey 동시 승인에서도 PG confirm 호출과 내부 Payment/PointHistory 반영이 중복되지 않음
- **롤백 가능성:** 중간. lock 또는 상태 전이 스키마 변경 여부에 따라 달라짐
- **추천 PR:** `fix(pay): guard duplicate payment confirmation`

- **작업 ID:** TASK-024
- **작업:** 부분 취소 멱등성 및 동시성 보강
- **해결할 이슈:** ARCH-030
- **대상 경로:** `pay/application/PaymentService`, `pay/Payment`, `pay/PaymentCancel`
- **선행 테스트:** TEST-026
- **변경 위험:** 부분 취소 재시도·중복 요청 응답 방식 변화
- **완료 조건:** 같은 부분 취소 요청이 재전송되거나 병렬 실행되어도 취소 이력·누적 취소 금액·포인트 차감이 한 번만 반영됨
- **롤백 가능성:** 중간. 취소 멱등 키 스키마 변경 도입 전에는 높음
- **추천 PR:** `fix(pay): make partial cancellation idempotent`

- **작업 ID:** TASK-027
- **작업:** mypage 조회·명령 Service 분리와 가족 정책 위임
- **해결할 이슈:** ARCH-032, ARCH-011
- **대상 경로:** `mypage/application`, `member/family`, `location/parentlocation`, S3·geocoding 연동
- **선행 테스트:** TEST-028
- **변경 위험:** 마이페이지 프로필·가족관리 API 동작과 외부 실패 정책 회귀
- **완료 조건:** 조회 전용 Service와 회원·가족 변경 Command Service가 분리되고 방장·가족 접근 검증이 공통 정책으로 이동함
- **롤백 가능성:** 중간
- **추천 PR:** `refactor(mypage): split profile queries and family commands`

### 9.4 4단계: 외부 부수효과 분리

- **작업 ID:** TASK-005
- **작업:** 가입 지오코딩 트랜잭션 경계 정리
- **해결할 이슈:** ARCH-012
- **대상 경로:** `auth/application/senior`, `goal/addressbookmark`, `location/parentlocation`
- **선행 테스트:** TEST-006
- **변경 위험:** 가입 완료 시점과 HOME 안전구역 생성 시점 변경
- **완료 조건:** 외부 지연이 장기 DB 트랜잭션을 점유하지 않음 또는 동기 정책을 명시적으로 유지함
- **롤백 가능성:** 중간
- **추천 PR:** `refactor(auth): isolate senior signup geocoding boundary`

- **작업 ID:** TASK-010
- **작업:** 안전구역 원자 중복 차단과 FCM 후속 처리 경계 정리
- **해결할 이슈:** ARCH-003, ARCH-015
- **대상 경로:** `location/realtime`, `fcm/event/safezone`
- **선행 테스트:** TEST-008, TEST-011, TEST-013
- **변경 위험:** 위치 업데이트 응답 시점과 알림 전달 시점 변화
- **완료 조건:** 병렬 이탈에도 이벤트가 한 번 발행되고 FCM 실패가 위치 상태 갱신을 되돌리지 않음
- **롤백 가능성:** 중간
- **추천 PR:** `refactor(location): isolate safe-zone notification side effects`

- **작업 ID:** TASK-011
- **작업:** AI 이상 판정과 심박 저장 트랜잭션 경계 분리
- **해결할 이슈:** ARCH-017
- **대상 경로:** `heart/application`, AI client 설정
- **선행 테스트:** TEST-013
- **변경 위험:** AI 실패 시 심박 기록 처리 정책 변경 가능성
- **완료 조건:** 외부 AI 호출이 장기 저장 트랜잭션을 점유하지 않고 실패 정책이 테스트로 고정됨
- **롤백 가능성:** 중간
- **추천 PR:** `refactor(heart): isolate anomaly detection transaction boundary`

- **작업 ID:** TASK-019
- **작업:** 앨범 영상 처리 실패 상태와 S3 보상 삭제 정책 정리
- **해결할 이슈:** ARCH-025
- **대상 경로:** `album/application/AlbumVideoProcessingService`, `album/application/AlbumFileService`, `album/Album`, S3 연동
- **선행 테스트:** TEST-021
- **변경 위험:** 영상 업로드 실패 시 사용자 노출 상태와 운영 재처리 방식 변화
- **완료 조건:** 영상 처리 실패·부분 업로드 실패 시 앨범 상태, 재시도 가능성, S3 정리 정책이 테스트와 문서로 고정됨
- **롤백 가능성:** 중간. 실패 상태 스키마 변경 도입 전에는 높음
- **추천 PR:** `refactor(album): model video processing failure policy`

- **작업 ID:** TASK-020
- **작업:** 앨범 알림 이벤트 소유권과 FCM 실패 격리
- **해결할 이슈:** ARCH-026
- **대상 경로:** `album/application`, `fcm/event/album`, `fcm/application/FcmService`
- **선행 테스트:** TEST-022
- **변경 위험:** 알림 발송 시점과 실패 처리 방식 변화
- **완료 조건:** 앨범 쓰기 트랜잭션은 FCM 전송 실패로 롤백되지 않고, 이벤트 계약 소유권이 album/application 경계로 정리됨
- **롤백 가능성:** 중간
- **추천 PR:** `refactor(album): isolate fcm notification side effects`

- **작업 ID:** TASK-022
- **작업:** 결제 PG 호출과 포인트 반영 실패 경계 Characterization Test 및 보정 정책 설계
- **해결할 이슈:** ARCH-028
- **대상 경로:** `pay/application/PaymentService`, `pay/config/PaymentClient`, `member/application/SeniorProfileService`
- **선행 테스트:** TEST-024
- **변경 위험:** PG 성공 후 내부 실패 재시도·보정 정책이 명시되며 운영 절차가 바뀔 수 있음
- **완료 조건:** PG 승인·취소 성공 뒤 내부 저장 또는 포인트 반영 실패의 현재 동작이 테스트로 고정되고, 보정 경로 설계가 LLD-0003에 반영됨
- **롤백 가능성:** 테스트만 추가하는 단계는 높음, 보정 상태/스키마 도입 후 중간
- **추천 PR:** `test(pay): characterize pg and point transaction boundary`

- **작업 ID:** TASK-029
- **작업:** AdminAuditLog 트랜잭션 정책 통일
- **해결할 이슈:** ARCH-034
- **대상 경로:** `admin/AdminAuditLog`, `admin/application/AdminAuditLogService`, `AdminAuthService`, `AdminMemberService`, `AdminFcmService`
- **선행 테스트:** TEST-030
- **변경 위험:** 감사 로그 기록 시점과 롤백 시 잔존 여부 변화
- **완료 조건:** 관리자 로그인·상태변경·FCM 테스트 로그가 동일한 성공/시도/실패 정책과 트랜잭션 전파로 기록됨
- **롤백 가능성:** 높음
- **추천 PR:** `refactor(admin): standardize audit log transaction policy`

### 9.5 5단계: ADR·LLD 정리

- **작업 ID:** TASK-006
- **작업:** ADR-0002 정합성 갱신 및 신규 ADR 판단
- **해결할 이슈:** ARCH-008, ARCH-010, ARCH-011
- **대상 경로:** `docs/adr`, `docs/lld`
- **선행 테스트:** TASK-001~004의 결정 완료
- **변경 위험:** 없음. 단, 구현과 문서가 불일치하지 않도록 동시 검토 필요
- **완료 조건:** Refresh Token·가족 접근·Membership 정책의 근거 문서가 최신 상태
- **롤백 가능성:** 높음
- **추천 PR:** `docs(adr): align auth and family access decisions`

- **작업 ID:** TASK-012
- **작업:** location/heart ADR·LLD 정합성 갱신 및 심박 이상 알림 정책 결정
- **해결할 이슈:** ARCH-018, ARCH-003, ARCH-014
- **대상 경로:** `docs/adr`, `docs/lld`
- **선행 테스트:** TEST-010~013과 TASK-009~011의 정책 결정
- **변경 위험:** 없음. 구현 결정을 문서에 정확히 반영해야 함
- **완료 조건:** LLD-0001의 구독·중복 정책과 LLD-0002의 심박 알림 정책이 코드 결정과 일치함
- **롤백 가능성:** 높음
- **추천 PR:** `docs(lld): align location and heart delivery policies`

- **작업 ID:** TASK-030
- **작업:** Critical·High 이슈 리팩터링 전 테스트·ADR·LLD 게이트 확정
- **해결할 이슈:** ARCH-008~011, ARCH-013~014, ARCH-016, ARCH-019~021, ARCH-023~024, ARCH-026, ARCH-028~030, ARCH-032
- **대상 경로:** `docs/architecture`, `docs/adr`, `docs/lld`, Critical·High 관련 테스트 패키지
- **선행 테스트:** TEST-001~004, TEST-009~012, TEST-014~016, TEST-019~020, TEST-022, TEST-024~026, TEST-028
- **변경 위험:** 낮음. 문서·테스트 계획 변경이지만 실제 리팩터링 착수 순서를 제한함
- **완료 조건:** Critical·High 이슈별 문서 상태와 필수 선행 테스트가 명확히 연결되고, 최종 통합 리뷰에서 PR 순서의 게이트로 사용됨
- **롤백 가능성:** 높음
- **추천 PR:** `docs(architecture): align critical-high test and adr gates`

## 10. 권장 PR 목록

| PR 순서 | PR 제목 | 해결할 이슈 | 선행 테스트 | 주요 대상 | 위험도 | 상태 |
| ----- | ----- | ------ | ------ | ----- | --- | -- |
| 1 | `fix(auth): validate rotated refresh token value` | ARCH-008 | TEST-001 | auth/global security | 높음 | 리뷰 완료 (구현·독립 리뷰 APPROVE, 커밋·PR 생성은 사용자 지시 대기) |
| 2 | `fix(auth): restrict senior provisioning to guardians` | ARCH-009 | TEST-002 | auth senior signup | 중간 | 예비 후보 |
| 3 | `refactor(member): centralize family access policy` | ARCH-004, ARCH-011 | TEST-004, TEST-005 | member/global/AOP | 중간 | 예비 후보 |
| 4 | `refactor(member): define membership withdrawal lifecycle` | ARCH-010 | TEST-003, TEST-007 | member/auth withdrawal | 높음 | 예비 후보 |
| 5 | `test(architecture): enforce module and layer boundaries` | ARCH-007 | TEST-005 | test/CI | 낮음 | 예비 후보 |
| 6 | `refactor(auth): isolate senior signup geocoding boundary` | ARCH-012 | TEST-006 | auth/location | 중간 | 예비 후보 |
| 7 | `fix(location): authorize parent location mutations` | ARCH-013, ARCH-011 | TEST-009, TEST-004 | parentlocation/member | 높음 | 예비 후보 |
| 8 | `fix(websocket): authorize family-scoped subscriptions` | ARCH-014, ARCH-011 | TEST-010, TEST-004 | global/websocket/location/heart | 높음 | 예비 후보 |
| 9 | `refactor(heart): make heart-rate ingestion idempotent` | ARCH-016 | TEST-012 | heart/DB/WebSocket DTO | 높음 | 예비 후보 |
| 10 | `refactor(location): isolate safe-zone notification side effects` | ARCH-003, ARCH-015 | TEST-008, TEST-011, TEST-013 | location/fcm | 중간 | 예비 후보 |
| 11 | `refactor(heart): isolate anomaly detection transaction boundary` | ARCH-017 | TEST-013 | heart/AI | 중간 | 예비 후보 |
| 12 | `docs(lld): align location and heart delivery policies` | ARCH-018 | TEST-010~013 | docs/location/heart/fcm | 낮음 | 예비 후보 |
| 13 | `refactor(goal): centralize health reward point policy` | ARCH-019 | TEST-014, TEST-017 | healthschedule/walk/medicine/member | 높음 | 예비 후보 |
| 14 | `fix(medicine): enforce medication proof idempotency` | ARCH-021 | TEST-015 | medicineschedule/S3/DB | 높음 | 예비 후보 |
| 15 | `fix(medicine): make medication reward settlement idempotent` | ARCH-020 | TEST-016 | medicineschedule/member point | 높음 | 예비 후보 |
| 16 | `test(goal): characterize health dashboard query composition` | ARCH-022, ARCH-005 | TEST-018 | goal/home | 낮음 | 예비 후보 |
| 17 | `test(album): characterize interaction idempotency and counters` | ARCH-023 | TEST-019 | album like/view/unlock | 높음 | 예비 후보 |
| 18 | `test(album): characterize album visibility policy` | ARCH-024 | TEST-020 | album permission/feed | 높음 | 예비 후보 |
| 19 | `refactor(album): model video processing failure policy` | ARCH-025 | TEST-021 | album/S3/FFmpeg | 중간 | 예비 후보 |
| 20 | `refactor(album): isolate fcm notification side effects` | ARCH-026 | TEST-022 | album/fcm/event | 높음 | 예비 후보 |
| 21 | `fix(fcm): handle token ownership transfer` | ARCH-027 | TEST-023 | fcm token | 중간 | 예비 후보 |
| 22 | `test(pay): characterize pg and point transaction boundary` | ARCH-028 | TEST-024 | pay/member/PG | 높음 | 예비 후보 |
| 23 | `fix(pay): guard duplicate payment confirmation` | ARCH-029 | TEST-025 | pay confirm | 높음 | 예비 후보 |
| 24 | `fix(pay): make partial cancellation idempotent` | ARCH-030 | TEST-026 | pay cancel | 높음 | 예비 후보 |
| 25 | `refactor(pay): move payment client to infrastructure package` | ARCH-031 | TEST-005 | pay client/config | 낮음 | 예비 후보 |
| 26 | `test(home): characterize dashboard query composition` | ARCH-005 | TEST-027 | home/query | 중간 | 예비 후보 |
| 27 | `refactor(mypage): split profile queries and family commands` | ARCH-032, ARCH-011 | TEST-028 | mypage/member/family | 중간 | 예비 후보 |
| 28 | `test(admin): characterize dashboard and search queries` | ARCH-033, ARCH-005 | TEST-029 | admin/query | 중간 | 예비 후보 |
| 29 | `refactor(admin): standardize audit log transaction policy` | ARCH-034 | TEST-030 | admin/audit | 낮음 | 예비 후보 |
| 30 | `docs(architecture): align critical-high test and adr gates` | Critical·High 이슈 전반 | TEST-001~030 | docs/architecture, docs/adr, docs/lld | 낮음 | 예비 후보 |

## 11. 결정 기록

### 2026-07-15 — 현재 모듈 구조의 분류

- **배경:** 1차 멀티 모듈 및 패키지 구조 리뷰.
- **결정:** 현재 구조를 `Entity 모듈과 Application 모듈을 나눈 구조`로 기록한다.
- **근거:** `widyu-api -> widyu-domain` 단방향 의존, Entity와 Repository의 모듈 분리.
- **검토한 대안:** 정상적인 순수 DDD 멀티 모듈, 배포 단위만 분리된 구조.
- **영향 범위:** 이후 모든 도메인 리뷰의 기준선.
- **관련 이슈:** ARCH-001, ARCH-002, ARCH-007.
- **ADR 작성 여부:** 아니오. 리뷰 분류이며 기술 결정이 아님.

### 2026-07-15 — 가족 접근 정책의 목표 위치

- **배경:** global Aspect와 여러 Service의 가족 관계 검증 중복 확인.
- **결정:** 아직 구현 결정은 하지 않되, 정책 소유 후보를 `member/family` Application 경계로 기록한다.
- **근거:** FamilyMembership Repository와 MemberType을 직접 사용하며 global의 범용성에 맞지 않는다.
- **검토한 대안:** global Aspect 유지, 각 Service 수동 검증 유지.
- **영향 범위:** AOP, home, location, heart, auth.
- **관련 이슈:** ARCH-004, ARCH-011.
- **ADR 작성 여부:** 구현 방향 확정 시 ADR-0002 개정 또는 신규 ADR 검토.

### 2026-07-15 — 위치 상태 모델과 안전구역 설정의 분리

- **배경:** `SeniorLocation`, trail, stay가 서로 다른 Redis TTL 모델이고 `ParentLocation`은 JPA 안전구역 설정임을 확인했다.
- **결정:** 최신 위치·이력·체류 상태는 분리된 Read/Cache 모델로 유지하며, `ParentLocation`과 병합하지 않는다.
- **근거:** 보존 기간과 갱신 빈도, 조회 목적이 서로 다르다.
- **검토한 대안:** 위치 이력·안전구역을 하나의 JPA Aggregate 또는 하나의 RedisHash로 통합.
- **영향 범위:** location Redis 키, parentlocation, healthschedule 방문인증.
- **관련 이슈:** ARCH-002, ARCH-003, ARCH-013.
- **ADR 작성 여부:** 아니오. 단기 구조 판단이며 Redis 보존 정책 변경 시 ADR 검토.

### 2026-07-15 — 위치·심박 topic의 가족 접근 요구

- **배경:** REST 가족 접근 AOP와 달리 STOMP 구독 경로의 대상 memberId 검증이 없음을 확인했다.
- **결정:** location·heart topic 구독은 본인 또는 연결된 보호자로 제한해야 하며, 정책은 member/family Application 경계에서 재사용한다.
- **근거:** 위치·심박수는 가족 범위 개인정보다.
- **검토한 대안:** CONNECT 인증만으로 모든 topic 구독 허용.
- **영향 범위:** WebSocket interceptor, location, heart, global/member 경계.
- **관련 이슈:** ARCH-004, ARCH-011, ARCH-014.
- **ADR 작성 여부:** 구현 방식 확정 시 ADR-0002 개정 또는 신규 ADR 검토.

### 2026-07-15 — 심박 Event와 Emergency의 현재 경계

- **배경:** `HeartRateEmergency`가 `HeartRateEvent`와 FK나 식별자 연결 없이 Member만 공유함을 확인했다.
- **결정:** 두 모델은 별도 Aggregate 후보로 유지하고, 병합보다 원천 배치 멱등성과 추적성을 우선 보강한다.
- **근거:** Event는 보존 기간이 있는 측정 이력이고 Emergency는 별도 조회·보존되는 응급기록이다.
- **검토한 대안:** Emergency를 Event 컬렉션으로 편입하거나 별도 엔티티를 제거.
- **영향 범위:** heart 저장 모델, 응급기록 조회, 향후 알림.
- **관련 이슈:** ARCH-016, ARCH-017, ARCH-018.
- **ADR 작성 여부:** 멱등 키·알림 정책이 결정되면 ADR 또는 LLD 개정 검토.

### 2026-07-15 — 건강관리 하위 기능의 현재 경계

- **배경:** `goal/medicineschedule`, `goal/walk`, `goal/healthschedule`, `goal/home`과 domain의 `medicine`, `walk`, `healthschedule`, `goal/DailyGoalStatus`를 함께 검토했다.
- **결정:** medicine, walk, healthschedule은 하나의 Aggregate가 아니라 건강관리 상위 기능 아래의 독립 하위 유스케이스로 기록한다. `goal/home`은 쓰기 도메인이 아니라 Read Model/화면 조회 조합으로 본다.
- **근거:** 각 기능의 상태 전이와 보상·인증 기준이 다르고, `GoalHomeService`는 여러 Repository를 조합해 화면 지표를 만든다.
- **검토한 대안:** `goal`을 단일 Aggregate 또는 단일 도메인 서비스로 통합.
- **영향 범위:** goal 패키지 구조, home 조회 경계, 이후 건강관리 리팩터링 순서.
- **관련 이슈:** ARCH-022, ARCH-005.
- **ADR 작성 여부:** 패키지 재명명이나 모듈 분리까지 추진할 때 ADR 후보.

### 2026-07-15 — 복약 스케줄과 복약 인증의 생명주기 분리

- **배경:** `MedicineSchedule`은 유효기간 버전링을 가진 반복 템플릿이고 `MedicationProof`는 날짜별 수행 기록으로 증가한다.
- **결정:** `MedicineSchedule`과 `MedicineCategory`/`MedicineScheduleDetail`은 하나의 Aggregate 후보로 유지하되, `MedicationProof`는 독립 수행 기록 후보로 별도 멱등성 정책을 검토한다.
- **근거:** category/detail은 cascade + orphanRemoval로 schedule 생명주기에 종속되지만, proof는 포인트 정산·통계의 원천이며 대량 증가한다.
- **검토한 대안:** MedicationProof를 MedicineSchedule 내부 컬렉션으로 포함.
- **영향 범위:** MedicationProof unique 제약, 복약 정산 원장, 월별 통계.
- **관련 이슈:** ARCH-020, ARCH-021.
- **ADR 작성 여부:** 인증 멱등 키 또는 정산 원장 스키마가 확정되면 LLD-0007/0008 개정 우선.

### 2026-07-15 — 앨범 Root와 상호작용 기록의 현재 경계

- **배경:** `Album`이 미디어 상태와 `comments`, `likes`, `views` 컬렉션 및 카운터를 함께 보유하고, Like/View/Unlock은 별도 Repository와 unique 제약으로 관리됨을 확인했다.
- **결정:** `Album`은 콘텐츠·미디어 처리 상태의 Root 후보로 유지하되, Like/View/Unlock은 대량 증가하는 독립 상호작용 기록 후보로 기록한다. Comment는 답글 생명주기를 가진 댓글 스레드 Root 후보로 별도 검토한다.
- **근거:** Like/View/Unlock은 회원·앨범 unique 제약과 별도 조회 Repository를 갖고, `Album` 컬렉션으로 로딩하거나 생명주기를 직접 관리하지 않아도 유스케이스가 동작한다.
- **검토한 대안:** Comment/Like/View/Unlock을 모두 Album 내부 Entity로 유지.
- **영향 범위:** album 카운터, 좋아요·조회·잠금 해제 멱등성, 앨범 상세 조회.
- **관련 이슈:** ARCH-023.
- **ADR 작성 여부:** Aggregate 경계 변경 또는 스키마 변경 시 ADR/LLD 후보.

### 2026-07-15 — FCM의 현재 성격과 앨범 알림 이벤트 소유권

- **배경:** 앨범 Service가 `fcm/event/album/dto` 이벤트를 발행하고, 동기 `AlbumNotificationListener`가 FCM 전송과 알림 저장을 수행함을 확인했다.
- **결정:** FCM은 현재 독립 도메인 Aggregate보다 알림 인프라 저장 모델과 후속 처리 어댑터에 가깝게 기록한다. 앨범 알림 이벤트 계약은 장기적으로 fcm이 아니라 album/application 경계에서 소유하는 방향을 검토한다.
- **근거:** FCM 실패는 앨범 생성·좋아요·댓글·잠금 해제 성공 여부와 분리되어야 하며, 이벤트 계약이 fcm 패키지에 있으면 소스 도메인 변경 이유와 알림 인프라 변경 이유가 섞인다.
- **검토한 대안:** fcm 패키지 소유 이벤트 DTO와 동기 리스너를 유지.
- **영향 범위:** album Service, fcm listener, notification 저장, 알림 실패 처리.
- **관련 이슈:** ARCH-026, ARCH-027.
- **ADR 작성 여부:** FCM 전달 보장 수준과 outbox/retry 정책을 확정할 때 ADR 또는 LLD-0002 개정 후보.

### 2026-07-15 — 결제 주문과 결제 결과의 현재 경계

- **배경:** `PaymentOrder`는 주문 생성·만료·결제 완료 상태를 갖고, `Payment`는 PG 승인 결과·결제수단 스냅샷·취소 누적 상태를 갖는 별도 Entity임을 확인했다.
- **결정:** `PaymentOrder`와 `Payment`를 별도 Aggregate Root 후보로 기록한다. `PaymentCard`·`PaymentEasyPay`·`PaymentTransfer`·`PaymentVirtualAccount`는 Payment 내부 결제수단 스냅샷으로 보고, `PaymentCancel`은 현재 Payment 종속 이력이지만 부분 취소 멱등성 보강 시 독립 취소 기록 후보로 재검토한다.
- **근거:** 주문은 승인 전 생성·만료·소유권 검증을 담당하고, Payment는 PG 결과와 취소 이력을 담당한다. 취소 이력은 반복 증가하며 PG cancel transaction key를 저장할 가능성이 있다.
- **검토한 대안:** PaymentOrder와 Payment를 하나의 Entity로 병합.
- **영향 범위:** 결제 승인 멱등성, 부분 취소 모델, 관리자 결제 조회.
- **관련 이슈:** ARCH-029, ARCH-030.
- **ADR 작성 여부:** 상태 전이 또는 취소 멱등 키 스키마를 변경할 때 LLD-0003 개정 우선.

### 2026-07-15 — PG 호출과 내부 포인트 반영의 보정 필요성

- **배경:** `PaymentService`가 열린 DB 트랜잭션 안에서 PG confirm/cancel을 호출하고 같은 트랜잭션에서 Payment·PaymentOrder·SeniorProfile·PointHistory를 변경함을 확인했다.
- **결정:** 현재 구조는 LLD-0003의 의도대로 결제와 포인트 반영을 한 유스케이스로 묶되, 외부 PG 상태와 내부 DB 상태가 원자적으로 커밋될 수 없다는 위험을 별도 이슈로 관리한다.
- **근거:** PG 성공 후 내부 커밋 실패, 포인트 낙관적 락 충돌, 동시 승인/취소 요청은 테스트와 운영 보정 경로 없이는 안전성을 판단하기 어렵다.
- **검토한 대안:** 포인트 반영을 즉시 이벤트로 분리, 결제 confirm/cancel 외부 호출을 완전히 트랜잭션 밖으로 이동.
- **영향 범위:** 결제 승인·취소 API, 포인트 원장, PG 대사 운영.
- **관련 이슈:** ARCH-028, ARCH-029, ARCH-030.
- **ADR 작성 여부:** PG 재조회·보정 job 또는 결제 outbox/saga 도입 시 ADR 후보. 단기에는 LLD-0003 개정.

### 2026-07-15 — home/mypage/admin의 현재 경계

- **배경:** home, mypage, admin 패키지와 직접 참조 Repository/QueryDSL 구현을 검토했다.
- **결정:** home은 첫 화면 Read Model, admin은 운영 Read Model과 관리 Command 컨텍스트, mypage는 화면 조회와 회원·가족 Command가 섞인 유스케이스 묶음으로 기록한다. 세 영역을 독립 도메인 Aggregate로 보지 않는다.
- **근거:** 각 Service가 member/family, album, goal/medicine/walk/healthschedule, heart, pay, fcm 등 기존 도메인 Repository를 조합해 DTO를 만들거나 기존 Aggregate를 변경한다.
- **검토한 대안:** home/mypage/admin을 각각 독립 도메인으로 분리.
- **영향 범위:** home/admin Query Repository, mypage Command 분리, Repository 계약 정리.
- **관련 이슈:** ARCH-005, ARCH-032, ARCH-033.
- **ADR 작성 여부:** 조회 경계와 Projection 기준을 실제로 도입할 때 신규 ADR 후보.

### 2026-07-15 — AdminAuditLog의 정책 미확정

- **배경:** `AdminAuditLogService.log()`는 `REQUIRES_NEW`로 저장하지만 `AdminAuthService.login()`은 Repository를 직접 호출해 같은 트랜잭션에 저장한다.
- **결정:** AdminAuditLog는 유지하되, 성공 로그인지 시도 로그인지 실패 로그까지 포함하는지 정책을 확정하기 전까지 현재 차이를 구조적 이슈로 관리한다.
- **근거:** 감사 로그의 트랜잭션 전파가 작업별로 다르면 롤백 시 로그 잔존 여부가 달라진다.
- **검토한 대안:** 모든 로그를 현재처럼 각 Service에서 자유롭게 저장.
- **영향 범위:** admin login, member status change, FCM test send, audit log 조회.
- **관련 이슈:** ARCH-034.
- **ADR 작성 여부:** 감사 로그 보존·실패 기록 요구가 확정되면 ADR 후보.

### 2026-07-15 — Critical·High 리팩터링 전 테스트·문서 게이트

- **배경:** 모든 지정 도메인 상세 리뷰 이후 Critical·High 이슈와 Aggregate 경계 변경 전 보호해야 할 동작만 대상으로 ADR·LLD·테스트 정합성을 재확인했다.
- **결정:** 새 구조 이슈를 추가하지 않고 기존 ARCH-008~032의 선행 테스트와 문서 상태를 리팩터링 게이트로 사용한다. 특히 Refresh Token 회전, 가족 접근, ParentLocation CUD, WebSocket 구독, 심박/복약/결제/앨범 상호작용 멱등성은 구현 변경보다 테스트 고정을 먼저 수행한다.
- **근거:** 실제 테스트에는 정상 흐름과 단위 예외 검증이 다수 존재하지만, Redis Refresh Token 회전 값, STOMP `SUBSCRIBE` 인가, DB unique/lock, 외부 PG·FCM 실패, 동시성·재시도 시나리오가 Critical·High 리스크를 충분히 보호하지 못한다.
- **검토한 대안:** 문서 정합성 리뷰 없이 바로 리팩터링 PR 착수.
- **영향 범위:** TEST-001~030, TASK-030, 최종 통합 리뷰의 PR 순서 결정.
- **관련 이슈:** ARCH-008~011, ARCH-013~014, ARCH-016, ARCH-019~021, ARCH-023~024, ARCH-026, ARCH-028~030, ARCH-032.
- **ADR 작성 여부:** 아니오. 각 구현 결정이 확정되면 관련 ADR·LLD를 개별 개정한다.

### 2026-07-15 — ARCH-008 Refresh Token 회전 검증 구현

- **배경:** TEST-001·TASK-001·PR 1(`fix(auth): validate rotated refresh token value`)을 첫 리팩터링 사이클로 구현했다. 이슈 #395, 브랜치 fix/#395.
- **결정 (실제 적용한 Refresh Token 비교 정책):** `JwtTokenProvider.retrieveRefreshToken()`이 JWT 서명·만료 파싱 후 `validateRefreshTokenMatches(memberId, 요청값)`로 Redis 저장 `RefreshToken.token`과 요청 token 값을 `equals` 비교한다. 키 부재와 값 불일치 모두 `BusinessException(ErrorCode.INVALID_REFRESH_TOKEN)`으로 거부한다. guardian 재발급과 admin refresh가 같은 관문을 공유한다.
- **Redis 저장 횟수:** 재발급 1회당 저장 정확히 1회. `GuardianTokenService.reissueTokenPair()`의 `createRefreshTokenDto()` 이중 발급 호출을 제거하고 `generateTokenPair()` 내부 저장만 남겼다.
- **이전 토큰 재사용 차단 방식:** 별도 blacklist나 delete 없이, 같은 `memberId` 키를 새 token으로 덮어쓰는 회전과 저장값 비교의 조합으로 차단한다.
- **검토한 대안:** ① 키 존재 확인 유지(기각 — 회전 불변식 미보장), ② compare-and-set 원자 연산 도입(보류 — 단일/다중 세션 정책 결정과 함께 별도 검토), ③ `createRefreshTokenDto()` 메서드 삭제(보류 — 테스트 회귀 가드가 참조, 별도 정리 PR 후보).
- **관련 테스트:** `JwtTokenProviderTest` 9개(값 일치 성공·값 불일치 실패·타 회원 불일치 실패·만료 실패·저장 1회 포함), 신규 `JwtTokenProviderRedisTest` 4개(실제 Redis 회전·재사용 거부·로그아웃 후 거부, Redis 미가용 시 skip), `GuardianTokenServiceTest` 3개(`createRefreshTokenDto` 미호출 검증). 구현 전 7개 실패로 버그 재현을 확인한 뒤 구현으로 전부 통과.
- **ADR-0002 수정 여부:** 이번 구현에서 수정하지 않았다. 이유 — ADR-0002의 불일치 지점은 Refresh Token 쿠키 전달 방식이며, 이는 이번 PR의 명시적 제외 범위다. 저장값 비교 자체는 ADR-0002의 기존 결정과 충돌하지 않는다. 쿠키 전달·세션 정책이 결정되는 TASK-006에서 개정한다.
- **영향 범위:** `JwtTokenProvider`, `GuardianTokenService`, guardian·admin 재발급 경로.
- **관련 이슈:** ARCH-008. 후속 후보: `createRefreshTokenDto()` dead code 정리, 재발급 CAS 정책, ADR-0002 쿠키 정합화(TASK-006).

## 12. 진행 상태

- [x] 1차 멀티 모듈 및 프로젝트 구조 리뷰
- [x] member/family/auth 상세 리뷰
- [x] location/heart 상세 리뷰
- [x] goal/medicine/walk/healthschedule 상세 리뷰
- [x] album/fcm 상세 리뷰
- [x] pay 상세 리뷰
- [x] home/mypage/admin 상세 리뷰
- [x] 테스트·ADR·LLD 정합성 리뷰
- [ ] 최종 통합 리뷰
- [ ] 리팩터링 PR 순서 확정
- [x] 실제 리팩터링 시작 (2026-07-15, PR 1 / ARCH-008 구현·리뷰 완료)

## 13. 다음 작업

> 2026-07-15 갱신: PR 1(ARCH-008/TEST-001/TASK-001)은 구현·리뷰 완료. 커밋·PR 생성은 사용자 지시 대기. 다음 구현 후보는 권장 PR 목록 순서상 PR 2 `fix(auth): restrict senior provisioning to guardians` (ARCH-009, TEST-002, TASK-002)다. 아래 기존 계획 항목은 유지한다.

1. **순서:** 1
   **작업:** 최종 통합 리뷰에서 Critical·High 이슈와 P0 테스트를 기준으로 리팩터링 PR 순서를 확정한다.
   **입력 자료:** ARCH-008~011, ARCH-013~014, ARCH-016, ARCH-019~021, ARCH-023~024, ARCH-026, ARCH-028~030, ARCH-032, TEST-001~030, TASK-030, 현재 문서의 7.5장.
   **완료 조건:** 리팩터링 착수 전 반드시 추가할 테스트 PR과 기능 변경 PR의 순서가 확정된다.
   **문서에서 갱신할 섹션:** 9, 10, 11, 12, 13, 14.
2. **순서:** 2
   **작업:** 인증·가족·위치 접근 P0 테스트를 먼저 설계한다.
   **입력 자료:** TEST-001~004, TEST-009~010, `GuardianTokenServiceTest`, `JwtTokenProviderTest`, `FamilyAccessAspectTest`, `ParentLocationServiceTest`, `WsTokenServiceRedisTest`.
   **완료 조건:** Refresh Token 회전 값, 가족 접근 정책 일치, ParentLocation CUD 인가, WebSocket 구독 인가의 테스트 시나리오가 PR 단위로 나뉜다.
   **문서에서 갱신할 섹션:** 6, 9, 10, 14.
3. **순서:** 3
   **작업:** 결제 멱등성·외부 실패 P0 테스트를 설계한다.
   **입력 자료:** TEST-024~026, `PaymentServiceTest`, `PaymentIntegrationTest`, LLD-0003.
   **완료 조건:** PG 성공 뒤 내부 실패, 동시 승인, 부분 취소 재전송·동시성 테스트 시나리오가 분리된다.
   **문서에서 갱신할 섹션:** 6, 7, 9, 10, 14.
4. **순서:** 4
   **작업:** 건강관리·앨범 멱등성 및 FCM 실패 P0 테스트를 설계한다.
   **입력 자료:** TEST-014~016, TEST-019~022, `MedicationProofServiceTest`, `MedicineScheduleRewardSchedulerTest`, `AlbumLikeServiceTest`, `AlbumNotificationListenerTest`.
   **완료 조건:** 복약 인증·정산, 앨범 상호작용, 앨범 알림 실패 격리 테스트 시나리오가 분리된다.
   **문서에서 갱신할 섹션:** 6, 7, 9, 10, 14.

## 14. 변경 이력

| 날짜 | 변경 내용 | 관련 리뷰 또는 PR |
| -- | ----- | ----------- |
| 2026-07-15 | Living Document 최초 생성. 1차 구조 리뷰와 member/family/auth 상세 리뷰 결과, 이슈 ARCH-001~012, 테스트 계획 TEST-001~008, 작업 TASK-001~006 등록. | 아키텍처 리뷰 1차, member/family/auth 리뷰 |
| 2026-07-15 | location/parentlocation/heart 및 직접 연결된 Redis·WebSocket·이벤트 상세 리뷰 반영. ARCH-003 근거 갱신, ARCH-013~018, TEST-009~013, TASK-007~012 등록. | location/heart 상세 리뷰 |
| 2026-07-15 | goal/medicine/walk/healthschedule 및 관련 포인트·위치 검증 리뷰 반영. ARCH-019~022, TEST-014~018, TASK-013~016 등록. | goal/medicine/walk/healthschedule 상세 리뷰 |
| 2026-07-15 | album/fcm 및 앨범 업로드·알림과 연결된 S3·FFmpeg·비동기 이벤트 리뷰 반영. ARCH-023~027, TEST-019~023, TASK-017~021 등록. | album/fcm 상세 리뷰 |
| 2026-07-15 | pay 및 결제와 연결된 포인트·회원·외부 PG Client 리뷰 반영. ARCH-028~031, TEST-024~026, TASK-022~025 등록. | pay 상세 리뷰 |
| 2026-07-15 | home/mypage/admin 및 직접 참조 QueryDSL·Repository 리뷰 반영. ARCH-005 근거 갱신, ARCH-032~034, TEST-027~030, TASK-026~029 등록. | home/mypage/admin 상세 리뷰 |
| 2026-07-15 | Critical·High 이슈 중심 테스트·ADR·LLD 정합성 리뷰 반영. 새 구조 이슈 추가 없이 TASK-030과 PR 30 후보를 등록하고 테스트·문서 게이트를 확정. | 테스트·ADR·LLD 정합성 리뷰 |
| 2026-07-15 | ARCH-008 구현 완료 (이슈 #395, 브랜치 fix/#395). Refresh Token Redis 저장값 비교 도입, 재발급 이중 저장 제거. TEST-001 단위 9개 + Redis 통합 4개 + 서비스 3개 테스트 보강, 독립 리뷰 APPROVE, 전체 빌드 통과. ARCH-008·TEST-001·TASK-001 완료, PR 1 리뷰 완료로 상태 변경. 설계 문서 implementation-plans/ARCH-008-refresh-token-rotation.md 추가. | PR 1 `fix(auth): validate rotated refresh token value` |
