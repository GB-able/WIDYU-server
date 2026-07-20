# LLD-0010: 심박 AI 판정과 저장 트랜잭션 경계 분리

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #420 |
| 관련 ADR | ADR-0008 |
| 작성자 | Codex |
| 작성일 | 2026-07-20 |

## 1. 목적 / 배경

심박수 배치 처리에서 외부 AI 이상 판정 호출과 Redis/JPA 저장이 하나의 장기 트랜잭션에 결합되어 있다.
AI 호출은 트랜잭션 밖에서 수행하고, 저장은 판정 결과를 입력으로 받는 짧은 트랜잭션으로 분리한다.
AI 실패 시 원본 심박 기록을 저장하지 않는 현재 정책은 AI 실패 정책 테스트로 고정한다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api
- `HeartRateService.processHeartRates()`에서 외부 AI 호출을 저장 트랜잭션 밖으로 이동한다.
- `HeartRatePersistenceService`를 추가해 Redis 최신 상태, `HeartRateEvent`, `HeartRateEmergency` 저장을 하나의 짧은 `@Transactional` 경계로 묶는다.
- AI 실패 정책 테스트: AI 실패 시 원본 심박 기록을 저장하지 않는 현재 정책을 단위 테스트로 고정한다.
- 기존 배치 멱등성 검사(`existsByMemberIdAndMeasuredAt`)는 AI 호출 전에 유지한다.

### Out of scope

- AI 실패 시 원본 심박 기록을 보존하는 정책 변경
- AI 판정 모델 버전, 원본 응답, confidence 같은 판정 메타데이터 영속화
- WebSocket/REST API 요청·응답 계약 변경
- HeartRateEvent/HeartRateEmergency 데이터 모델 변경

## 3. 인터페이스 / API

기존 REST/WebSocket 계약 변경 없음.

- WebSocket: `/app/heart-rate/send`
- REST: 기존 `HeartRateService.processHeartRates()` 호출 경로
- 응답 DTO: `HeartRateStatusResponse` 변경 없음

## 4. 데이터 모델

신규 테이블, 컬럼, Redis 필드 없음.
엔티티 변경 없음.

## 5. 처리 흐름

`HeartRateService.processHeartRates(memberId, request)`

1. 회원 존재 여부를 조회한다. 회원이 없으면 `MEMBER_NOT_FOUND` 예외를 던지고 AI 호출과 저장을 수행하지 않는다.
2. 요청 배치의 최소 `measuredAt`을 `batchStart`로 계산한다.
3. `heartRateEventRepository.existsByMemberIdAndMeasuredAt(memberId, batchStart)`가 true이면 기존 최신 상태를 반환하고 AI 호출과 저장을 수행하지 않는다.
4. 요청의 15개 심박수 값을 추출한다.
5. `HeartRateAnomalyDetector.detectAnomaly()`를 호출한다. 이 단계는 저장 트랜잭션 밖에서 수행한다.
6. AI 판정 결과를 `HeartRateStatus`로 변환한다.
7. `HeartRatePersistenceService.saveAnalysis(memberId, request, status, isAbnormal)`를 호출한다.
8. `HeartRatePersistenceService.saveAnalysis()`는 public `@Transactional` 메서드로 회원을 다시 조회하고, `HeartRateResult` 저장, `HeartRateEvent` 15건 저장, 이상 판정 시 `HeartRateEmergency` 저장을 수행한다.
9. 저장된 `HeartRateResult`를 `HeartRateStatusResponse.from()`으로 변환해 반환한다.

트랜잭션 경계:

- AI 호출: 트랜잭션 없음
- 저장: `HeartRatePersistenceService.saveAnalysis()`의 `@Transactional`
- 조회 API: 기존 readOnly 트랜잭션 유지

`@EventListener`와 Facade는 사용하지 않는다.

## 6. 예외 / 에러 처리

- 회원이 없으면 기존과 동일하게 `MEMBER_NOT_FOUND`를 반환한다.
- 심박 데이터가 15개가 아니면 기존과 동일하게 `BAD_REQUEST`를 반환한다.
- AI 서버 통신 실패는 기존과 동일하게 `INTERNAL_SERVER_ERROR`를 반환하고 원본 심박 기록을 저장하지 않는다.
- 동시 중복 배치 요청에서 DB 유니크 제약 위반이 발생할 수 있는 기존 한계는 이번 범위에서 변경하지 않는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AI 호출은 저장 전용 `@Transactional` 메서드 밖에서 수행된다.
- [x] AI 실패 시 `HeartRateResult`, `HeartRateEvent`, `HeartRateEmergency`를 저장하지 않는다.
- [x] 회원이 없으면 AI 호출과 저장을 수행하지 않는다.
- [x] 중복 배치는 AI 호출과 저장 없이 기존 상태를 반환한다.
- [x] 신규 정상 배치는 `HeartRateResult`와 `HeartRateEvent`를 저장한다.
- [x] 신규 이상 배치는 `HeartRateResult`, `HeartRateEvent`, `HeartRateEmergency`를 저장한다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DB/Redis 스키마 변경 없음.
- MySQL ENUM 변경 없음.
- API 계약 변경 없음.
- 저장 로직이 `HeartRateService`에서 `HeartRatePersistenceService`로 이동한다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- Issue #420
- ADR-0008
