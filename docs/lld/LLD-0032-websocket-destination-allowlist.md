# LLD-0032: WebSocket 목적지 allowlist

| 항목 | 값 |
| --- | --- |
| 상태 | Approved (사용자가 승인한 #602 범위) |
| Issue | #602, #1027(6절 개정) |
| 관련 ADR | ADR-0002, ADR-0020, ADR-0017 |
| 작성자 | Codex |
| 작성일 | 2026-09-14 |

## 1. 목적 / 배경

기존 SUBSCRIBE 검사는 보호 토픽 접두사 밖의 `/topic/**`를 통과시켜 가족 검증을 우회할 수 있다. SEND는 목적지를 검사하지 않아 클라이언트가 브로커에 위치·심박 메시지를 직접 발송할 수 있다. 인바운드 채널에서 명시한 목적지만 허용한다.

## 2. 범위

- In scope: widyu-api의 inbound allowlist, 보호 토픽 outbound 현재 권한 재검증, 보안 회귀 테스트, 기존 ACK 호환성.
- Out of scope: #603의 명시적 연결 종료와 DB tokenversion, 관리자 필터, JwtTokenProvider, 운영/AWS 호출.

## 3. 인터페이스 / API

| 명령 | 정확한 목적지 | 조건 |
| --- | --- | --- |
| SUBSCRIBE | `/topic/location/senior/{memberId}` | 인증 및 FamilyAccessService 가족 인가 |
| SUBSCRIBE | `/topic/heart-rate/{memberId}` | 인증 및 FamilyAccessService 가족 인가 |
| SUBSCRIBE | `/user/queue/location/ack` | 인증된 사용자, Spring user destination 유지 |
| SUBSCRIBE | `/user/queue/heart-rate/result` | 인증된 사용자, 기존 발신 세션 ACK 유지 |
| SUBSCRIBE | `/user/queue/sensor/result` | 인증된 사용자, 발신 세션 센서 배치 ACK (#632, LLD-0041) |
| SUBSCRIBE | `/user/queue/errors` | 인증된 사용자, 기존 오류 응답 유지 |
| SEND | `/app/location/update` | 인증, 기존 발신자 검증 유지 |
| SEND | `/app/heart-rate/send-single` | 인증, 기존 단건 처리 유지 |
| SEND | `/app/sensor/batches/send` | 인증, 평시 1초 센서 배치 (#632, LLD-0041) |

memberId는 기존 계약인 ASCII 숫자 1~18자리를 유지한다. 존재·시니어 여부·가족 관계는 기존 서비스가 검증한다. 그 외 목적지와 목적지 누락은 거절한다. wildcard, suffix, raw `/queue`, 타 사용자 지정 `/user/{id}/queue`도 허용하지 않는다. HTTP·DTO·Swagger 변경은 없다.

## 4. 데이터 모델

DB 변경 없음. ERD-0001의 Member, SeniorProfile, FamilyMembership 관계를 기존 FamilyAccessService로 조회한다. 서버는 연결 중인 STOMP 세션 ID와 인증 회원 ID를 노드 로컬 메모리에 보관하고 disconnect 시 제거한다.

## 5. 처리 흐름

1. CONNECT는 기존 인증 처리를 유지한다.
2. SUBSCRIBE는 인증 회원을 확인하고 사용자 큐 4개 또는 정확한 가족 토픽인지 검사한다.
3. 가족 토픽은 추출한 대상 회원과 호출자 ID로 가족 검증 후 통과시킨다.
4. SEND는 인증 회원과 정확한 애플리케이션 목적지 3개를 확인한다.
5. heartbeat(command 없음), ACK/NACK, UNSUBSCRIBE, DISCONNECT 등 제어 프레임은 기존대로 통과시킨다.
6. 서버가 위치·심박 보호 토픽을 outbound로 전달할 때는 세션의 회원 ID를 찾고 현재 가족 관계와 양쪽 회원 ACTIVE 상태를 다시 검증한다. 관계 해제·정지·세션 매핑 누락·조회 오류면 해당 세션 메시지를 폐기한다.

inbound allowlist와 outbound 권한 재검증은 각각 clientInboundChannel, clientOutboundChannel에 적용한다. 트랜잭션·이벤트 경계는 변경하지 않는다.

## 6. 예외 / 에러 처리

인가 거절은 기존 SUBSCRIBE와 같이 null 반환으로 프레임을 폐기한다. 새 STOMP ERROR를 도입하지 않는다. outbound 가족·상태 조회 실패도 정보 노출을 막기 위해 fail-closed로 폐기한다.

**2026-11-12 개정(#1027): 거부한 SUBSCRIBE를 발신 세션에 알린다.** 처음에는 오류 큐 응답도 두지 않았다. 그러자 앱은 구독이 거부됐는지 위치가 아직 안 온 것인지 구분하지 못했다(QA N12, 사례 L-10).

- 인증된 세션의 SUBSCRIBE가 가족 인가에 실패하거나, 허용 목록 밖 목적지거나, 목적지가 없으면(`destination: ""`) 프레임은 지금처럼 버리고, 그 세션의 `/user/queue/errors`에 `{"error":"FORBIDDEN","message":"접근 권한이 없습니다.","destination":"<요청 목적지>"}`를 보낸다. 같은 회원의 다른 세션에는 보내지 않는다(`WebSocketWorkDispatcher.sendToSession`, LLD-0088).
- 기존 오류 형식(`error`, `message`)을 지키고 `error` 자리에 `ErrorCode.FORBIDDEN` 이름을 싣는다(LLD-0091의 `TEMPORARILY_BUSY`와 같은 방식). 여러 구독 중 어느 것이 거부됐는지 알 수 있게 `destination`을 더한다. 앱이 보낸 목적지를 돌려줄 뿐이라 새로 드러나는 정보는 없다.
- 대상 회원이 없거나 시니어가 아닌 경우(`BAD_REQUEST`)도 같은 `FORBIDDEN`이다. 코드가 갈리면 구독 시도로 회원 ID의 존재를 알아낼 수 있다.
- STOMP ERROR 프레임은 규격상 보낸 뒤 연결을 닫는다. 같은 연결의 심박 전송, 위치 구독, ACK까지 끊기므로 쓰지 않는다.
- 오류 전송이 실패해도 WARN만 남기고 구독은 거부한다. 인증 정보가 없는 세션은 받을 사용자가 없어 지금처럼 조용히 버린다.
- 앱은 `/user/queue/errors`를 보호 토픽보다 먼저 구독해야 이 오류를 받는다. 수신 실행기는 한 세션의 프레임 순서를 보장하지 않으므로(`setPreserveReceiveOrder` 미설정) 두 SUBSCRIBE를 연달아 보내면 오류를 놓칠 수 있다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1: 정확한 위치·심박 토픽은 가족 인가 후 허용하고 비가족은 거절한다.
- [x] AC2: 상위 wildcard, 변형 경로, raw 큐, 누락 목적지 구독은 거절한다.
- [x] AC3: 인증된 SEND는 세 경로만 허용하며 브로커 직접 발송·레거시 배치·변형 경로는 거절한다.
- [x] AC4: 인증 누락은 허용 목적지에서도 거절하며 Principal과 인증 세션 fallback을 유지한다.
- [x] AC5: 사용자 ACK·오류 큐 구독과 heartbeat·ACK/NACK·unsubscribe·disconnect를 유지한다.
- [x] AC6: 채널을 통한 차단과 정상 전달 회귀 테스트 및 `bash scripts/harness/verify.sh`가 통과한다.
- [x] AC7: 정상 구독 뒤 가족 관계가 해제되거나 보호자·시니어가 INACTIVE가 되면 이후 보호 토픽 outbound 메시지를 수신하지 않는다.
- [x] AC8 (#1027): 인증된 세션의 SUBSCRIBE가 거부되면 그 세션만 `/user/queue/errors`로 `FORBIDDEN`과 요청 목적지를 받고, 허용된 구독에는 오류를 보내지 않는다.

2026-09-14 검증: Java 21 하네스 정적 검사·compileJava·API 테스트 통과. 총 586건 중 577건 통과, 실패/오류 0건, 9건 건너뜀(로컬 Redis 부재 6건, 영상 fixture 부재 3건). JwtChannelInterceptorTest 68건은 모두 통과했으며 실제 Simple Broker 전달 회귀 2건을 포함한다. 기존 HeartRateWebSocketControllerTest의 발신 세션 ACK 검증도 통과했다. 실제 브라우저/SockJS 네트워크 E2E는 실행하지 않았다.

2026-09-14 추가 검증: `JwtChannelInterceptorTest`, `FamilyTopicOutboundInterceptorTest`, `FamilyAccessServiceTest`를 실행해 정상 구독 뒤 가족 해제 모사 시 위치·심박 outbound 수신 0건, 정지된 보호자·시니어의 현재 접근 거절을 확인했다. `bash scripts/harness/verify.sh --base "$(git merge-base origin/develop HEAD)"`도 통과했다. 실제 브라우저/SockJS 및 다중 서버 연결 종료 검증은 미실행이다.

2026-11-16 #1027 검증: `JwtChannelInterceptorTest` 94건과 `WebSocketSessionAckIntegrationTest` 7건이 실패와 오류 없이 통과했다. 실제 STOMP 클라이언트(SockJS) 시나리오에서 다른 가족의 위치 토픽을 구독한 세션만 오류 1건(`FORBIDDEN`, 요청 목적지, 필드 3개)을 받았고 같은 회원의 다른 세션은 0건이었다. 거부 뒤에도 연결은 유지돼 같은 세션이 심박과 위치 ACK를 1건씩 받았다. 실제 앱과 개발 서버에서는 확인하지 않았다.

## 8. 영향 범위 / 마이그레이션

DB 마이그레이션 없음. 미등록 목적지를 쓰는 클라이언트는 거절되며 신규 경로 추가 시 allowlist와 회귀 테스트를 함께 수정해야 한다. 신규 ADR N/A: 기존 ADR-0002·0020의 명시적 가족 인가를 STOMP 경계에서 누락 없이 적용하는 구현으로, 구체적인 경로·대안은 이 LLD에 기록한다.

접두사 차단 목록 확장은 상위 패턴 누락을 반복할 수 있어 제외한다. Spring Security 메시징 전면 도입은 인증·제어 프레임 설정까지 범위가 확대되므로 제외하고 기존 인터셉터의 정확한 allowlist를 선택한다.

## 미결정 사항(Open Questions)

없음. 연결 자체를 종료하고 다중 노드에서 세션을 전파 폐기하는 기능은 #603 범위로 남기되, 이 LLD 범위에서는 다음 보호 토픽 전달 전에 현재 권한을 재확인해 노출을 차단한다.

## 10. 참고

- [LLD-0001](LLD-0001-websocket-realtime-location.md), [LLD-0023](LLD-0023-heart-rate-single-measurement-transport.md).
- WebSocketConfig, RealtimeLocationController/Service, HeartRateWebSocketController, WebSocketExceptionHandler.
