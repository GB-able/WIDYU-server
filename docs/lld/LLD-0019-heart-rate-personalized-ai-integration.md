# LLD-0019: 개인화 심박 이상 감지 AI 연동

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #446 |
| 관련 ADR | ADR-0013 |
| 작성자 | Codex |
| 작성일 | 2026-07-26 |
| 개정일 | 2026-08-18 (AI ver7 배포 명세 반영) |

## 1. 목적 / 배경

심박 AI의 입력이 사용자별 단건 JSON 계약으로, 출력이 `alert`, `level`, `reason` 등을 포함한 구조화 응답으로 변경되었다.
기존 WebSocket 배치를 유지하면서 사용자 식별값, 활동 상태, 측정 시각을 새 AI 계약에 맞춰 전달하고 단계별 판정 결과를 저장한다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api, widyu-domain
- WebSocket `HeartRateSendRequest`에 배치 수준 `context`를 추가한다.
- `REST`, `LOW`, `ACTIVE`, `UNKNOWN`만 허용하고 null·빈 문자열·공백은 `UNKNOWN`으로 정규화한다.
- 심박수 입력 범위를 1~299로 검증한다. AI가 0과 300을 400으로 거부하므로 같은 범위로 맞춘다.
- 15개 측정값을 측정 시각 오름차순으로 AI `POST /api/hr`에 순차 전송한다.
- AI의 `NORMAL`, `CAUTION`, `EMERGENCY`를 기존 심박 상태·이벤트에 반영한다.
- 배치 중 `alert=true`인 `EMERGENCY`가 하나 이상이면 `HeartRateEmergency`를 저장한다.
- Docker AI 이미지를 `ryuchanghoon/widyu-ai-ver7:latest`로 갱신한다.

### Out of scope

- AI Flask 애플리케이션 구현과 개인 기준선 저장 방식
- `CAUTION` 빈도 기반 FCM 알림과 보호자 메시지 정책
- AI 응답의 `reason`, `layer`, `held_seconds`, `baseline_source`, `sample_count` 영속화
- 기존 WebSocket 배치를 단건 전송으로 변경
- 동일 사용자의 동시 배치를 인스턴스 간 직렬화
- AI 장애 시 원본 심박 기록을 저장하는 정책 변경

## 3. 인터페이스 / API

기존 WebSocket 경로와 응답 필드명은 유지한다.

```text
SEND /app/heart-rate/send
SUBSCRIBE /topic/heart-rate/{memberId}
ACK /user/queue/heart-rate/result
```

WebSocket 요청:

```json
{
  "heartRates": [
    {
      "heartRate": 70,
      "measuredAt": "2026-07-26T12:00:00"
    }
  ],
  "location": "서울시",
  "context": "REST"
}
```

- `heartRates`: 정확히 15개
- `heartRate`: 정수, 1~299 (AI가 0과 300을 거부한다. 응답 메시지는 `BPM must be between 0 and 300`이지만 실제 통과 범위는 양끝 배타적이다)
- `context`: `REST`, `LOW`, `ACTIVE`, `UNKNOWN`; null·빈 문자열·공백은 `UNKNOWN`

AI 요청:

```http
POST {AI_SERVER_URL}/api/hr
Content-Type: application/json
```

```json
{
  "user_id": "1023",
  "bpm": 70,
  "context": "REST",
  "timestamp": 1785034800.0
}
```

`timestamp`는 AI 명세상 선택값이며 미입력 시 AI 서버 현재 시각이 적용된다. 백엔드는 측정 시각 순서를 보존해야 하므로 항상 명시한다.

AI 응답 전체:

```json
{
  "alert": true,
  "layer": "L0",
  "level": "EMERGENCY",
  "reason": "tachycardia",
  "bpm": 160.0,
  "context": "ACTIVE",
  "timestamp": 1784952000.0,
  "held_seconds": 30.0,
  "baseline_source": "PRIOR",
  "sample_count": 10
}
```

백엔드가 역직렬화하는 필드는 `alert`, `level` 둘뿐이며, 나머지는 `@JsonIgnoreProperties(ignoreUnknown = true)`로 폐기한다.

| 필드 | 값 | 백엔드 사용 |
| --- | --- | --- |
| `alert` | `false` 정상 / `true` 이상 감지 | 사용 (위급상황 저장 판정) |
| `level` | `NORMAL`, `CAUTION`, `EMERGENCY` | 사용 (`HeartRateStatus`로 저장) |
| `layer` | `L0` 고정 임계값 / `L1` 개인 기준선 | 미사용 |
| `reason` | `normal`, `flatline_arrest`(극저심박), `bradycardia`(서맥), `tachycardia`(빈맥), `resting_tachycardia`(휴식기 빈맥), `personal_hr_high`, `personal_hr_low` | 미사용 |
| `baseline_source` | `PRIOR` 기본 기준선 / `PERSONAL` 개인 기준선 | 미사용 |
| `bpm`, `context`, `timestamp`, `held_seconds`, `sample_count` | 요청 에코 및 판정 근거 | 미사용 |

`level`과 `reason`은 서로 다른 축이다. `level`은 심각도, `reason`은 사유이며 서맥·빈맥 구분은 `reason`에만 존재한다.
`HeartRateStatus`에는 심각도만 저장되므로 조회 API로는 이상 사유를 알 수 없다.

AI 내부 판별 방식은 `context`에 따라 갈린다. 아래는 `ryuchanghoon/widyu-ai-ver7:latest` 컨테이너에 직접 요청해 확인한 실측 결과다(2026-08-18).

| `context` | 판별 계층 | 기준 |
| --- | --- | --- |
| `REST` | L1 (개인 기준선) | 초기 심박 30개 수집 후 개인 기준선 생성 |
| `LOW`, `ACTIVE`, `UNKNOWN` | L0 (고정 임계값) | 서맥·빈맥·극저심박이 30초 이상 지속되면 이상 |

`baseline_source`는 `context`와 무관하게 30개 수집 후 `PERSONAL`로 바뀐다. `context`에 따라 갈리는 것은
**판정 계층(`layer`)뿐**이므로, 개인화 적용 여부는 `baseline_source`가 아니라 `layer`로 판단해야 한다.
`REST`가 아닌 경우 `sample_count`는 30에서 멈춘다.

**확인 필요**: 실측에서 `context=REST`(L1 경로)는 190bpm을 60초 지속시켜도 `EMERGENCY`를 반환하지 않았다.
정상 심박 35개로 개인 기준선을 만든 뒤 급등시킨 경우에도 같았다. 반면 `ACTIVE`·`UNKNOWN`·`LOW`(L0 경로)는
동일 조건에서 31초째 `EMERGENCY`(`tachycardia`)를 반환했다.
따라서 앱이 `context=REST`를 보내기 시작하면 휴식 중 위급 상황을 감지하지 못할 수 있다.
AI 담당자 확인 전까지 `context` 전송 정책을 변경하지 않는다.

WebSocket 응답 예시:

```json
{
  "memberId": 1023,
  "heartRateStatus": "CAUTION",
  "heartRate": 120,
  "measuredAt": "2026-07-26T12:00:14"
}
```

## 4. 데이터 모델

- `HeartRateStatus`에 `CAUTION`, `EMERGENCY`를 추가한다.
- 기존 MySQL/Redis 데이터의 `ANOMALY` 역직렬화 호환을 위해 `ANOMALY`는 삭제하지 않는다.
- 신규 테이블·컬럼·Redis 필드는 없다.
- AI 요청·응답 DTO는 `widyu-api`의 `HeartRateAnomalyDetector` 내부 전용 타입으로 둔다.

## 5. 처리 흐름

1. `HeartRateService.processHeartRates()`가 회원 존재와 기존 배치 멱등성을 확인한다.
2. `HeartRateAnomalyDetector.detect()`에 `memberId`, 측정값 15개, 정규화한 `context`를 전달한다.
3. Detector는 측정값을 `measuredAt` 오름차순으로 정렬한다.
4. 각 측정값을 `user_id=memberId`, `bpm=heartRate`, `context`, Unix timestamp로 변환해 AI에 순차 전송한다.
5. 응답 `level`의 배치 내 최댓값을 최종 상태로 사용한다: `EMERGENCY > CAUTION > NORMAL`.
6. `alert=true`이면서 `level=EMERGENCY`인 응답이 하나 이상이면 최종 판정을 위급상황으로 표시한다.
7. `HeartRatePersistenceService.saveAnalysis()`가 기존 짧은 트랜잭션에서 최신 결과와 15개 이벤트를 저장하고, 위급상황일 때만 `HeartRateEmergency`를 저장한다.
8. 기존 `HeartRateStatusResponse`로 WebSocket topic과 발신자 ACK에 결과를 전달한다.

트랜잭션 경계는 ADR-0008을 유지한다.

- AI 순차 호출: 트랜잭션 없음
- Redis/JPA 저장: `HeartRatePersistenceService.saveAnalysis()`의 `@Transactional`

Facade와 신규 의존성은 추가하지 않는다.

## 6. 예외 / 에러 처리

- 심박 데이터가 15개가 아니면 `BAD_REQUEST`를 반환한다.
- 심박수가 1 미만 또는 299 초과이면 요청 검증 오류를 반환한다. 워치 미착용 시 올라올 수 있는 0을 서버에서 거르지 않으면 AI가 400을 반환해 배치 15개 전체가 저장되지 않는다.
- `context`가 허용값 또는 공백이 아니면 요청 검증 오류를 반환한다.
- AI 통신 실패, 빈 응답, 지원하지 않는 `level`은 `INTERNAL_SERVER_ERROR`를 반환하고 배치를 저장하지 않는다.
- AI 호출이 일부 성공한 뒤 실패하면 AI 서비스의 개인 기준선 상태는 이미 변경될 수 있다. 재처리 정책은 기존 배치 멱등성 범위 밖으로 둔다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AI 요청은 JSON이며 `user_id`, `bpm`, `context`, `timestamp`를 포함한다.
- [x] 15개 측정값을 측정 시각 순서대로 AI에 호출한다.
- [x] null·빈 문자열·공백 `context`는 `UNKNOWN`으로 전달한다.
- [x] 허용되지 않은 `context`와 1~299 밖의 심박수는 거절한다.
- [x] AI `NORMAL`, `CAUTION`, `EMERGENCY` 중 배치 내 가장 높은 상태를 저장·반환한다.
- [x] `alert=true`인 `EMERGENCY`만 `HeartRateEmergency` 저장 대상으로 전달한다.
- [x] AI 실패 시 `HeartRateResult`, `HeartRateEvent`, `HeartRateEmergency`를 저장하지 않는다.
- [x] Docker Compose가 `ryuchanghoon/widyu-ai-ver7:latest`를 사용한다.
- [x] 기존 `ANOMALY` 상태 데이터와 조회 계약이 호환된다.
- [x] `./gradlew compileJava`와 `bash scripts/harness/run-module-tests.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- `HeartRateSendRequest`에 선택적 `context` 필드가 추가된다. 기존 미전송 클라이언트는 `UNKNOWN`으로 처리된다.
- `HeartRateStatus`의 신규 값이 REST 조회, 홈 카드, WebSocket 응답에 노출될 수 있다.
- `HeartRateEvent.status`는 문자열 컬럼이므로 MySQL ENUM 변경 명령은 필요하지 않다.
- 엔티티 enum 변경 후 QueryDSL Q-클래스를 재생성한다.
- AI 컨테이너 교체 시 AI가 메모리에 보관한 개인 기준선은 초기화될 수 있다.

## 9. 미결정 사항 (Open Questions)

- `context=REST`(L1 경로)에서 위급 상황이 감지되지 않는 이유. AI 담당자 확인이 필요하다. 답변 전까지 `context` 전송 정책을 바꾸지 않는다.
- 워치가 실제로 `context`를 전송하는지. 서버 로그의 `rawContext`로 확인한다.

## 10. 참고

- Issue #446
- ADR-0008
- ADR-0013
- AI image: `ryuchanghoon/widyu-ai-ver7:latest`
