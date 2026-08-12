# LLD-0020: 심박 EMERGENCY 보호자 FCM 알림

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #448 |
| 관련 ADR | ADR-0014 |
| 작성자 | Codex |
| 작성일 | 2026-07-26 |

## 1. 목적 / 배경

심박 AI의 `EMERGENCY` 판정은 응급기록으로 남지만 보호자 FCM 알림으로 전달되지 않는다.
EMERGENCY만 즉시 알리고 CAUTION은 후속 정책으로 남긴다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api
- 신규 EMERGENCY 심박 배치가 저장된 뒤 같은 가족의 보호자 전원에게 FCM을 발송한다.
- 기존 `HEART_MESSAGE` 카테고리와 `FcmService`를 사용한다.
- 중복 배치, NORMAL, CAUTION은 FCM을 발송하지 않는다.
- 보호자 한 명의 FCM 발송 실패가 심박 저장 또는 다른 보호자 알림을 막지 않게 한다.

### Out of scope

- CAUTION 빈도 기반 알림
- FCM 재시도·아웃박스·전용 알림 카테고리
- 심박 긴급 알림 전용 사용자 설정
- WebSocket 응답 또는 REST API 계약 변경

## 3. 처리 흐름

1. `HeartRateService.processHeartRates()`는 기존 배치 시작 시각 멱등성을 확인한다.
2. 새 배치가 `EMERGENCY`로 판정되면 `HeartRatePersistenceService.saveAnalysis()`가 응급기록을 포함해 저장한다.
3. 저장 성공 뒤 `HeartRateService`가 `HeartRateEmergencyEvent`를 발행한다.
4. `HeartRateEmergencyNotificationService` 리스너가 시니어와 같은 가족의 보호자 목록을 조회한다.
5. 보호자마다 기존 `FcmService.sendMessageToUser()`를 `HEART_MESSAGE` 카테고리로 호출한다.
6. 보호자별 발송 예외는 기록하고 다음 보호자 발송을 계속한다.
7. 중복 배치는 기존 상태를 즉시 반환하므로 알림 단계에 도달하지 않는다.

## 4. 알림 내용

| 필드 | 값 |
| --- | --- |
| 제목 | `{시니어 이름}님의 심박수 이상이 감지되었습니다` |
| 본문 | `현재 상태를 확인해주세요.` |
| 카테고리 | `HEART_MESSAGE` |
| 이미지 | 시니어 프로필 이미지 |

## 5. 예외 / 에러 처리

- 시니어 프로필, 가족 또는 보호자가 없으면 알림을 생략하고 심박 처리 성공을 유지한다.
- FCM 설정 OFF는 기존 `FcmService` 정책에 따라 발송하지 않는다.
- FCM 런타임 예외는 보호자 단위로 로그를 남기고 전파하지 않는다.
- 심박 분석·저장 실패 시 알림을 발송하지 않는다.

## 6. 인수조건 (Acceptance Criteria)

- [x] 신규 EMERGENCY 배치는 같은 가족 보호자 전원에게 FCM 발송을 요청한다.
- [x] NORMAL과 CAUTION 배치는 FCM을 요청하지 않는다.
- [x] 동일 배치를 재전송하면 FCM을 중복 요청하지 않는다.
- [x] 한 보호자 FCM 발송 실패가 다른 보호자 발송과 심박 처리 성공을 막지 않는다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 7. 미결정 사항 (Open Questions)

없음.
