# LLD-0189: 보호자 홈 진입은 어르신께 위치 조회 알림을 보내지 않는다

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1163 |
| 관련 ADR | [ADR-0036](../adr/ADR-0036-privacy-records-consent-access-logs.md) 결정 3·4. [LLD-0130](LLD-0130-location-access-notice-immediate.md) 「열람마다 즉시 통보」와 [LLD-0179](LLD-0179-location-access-notice-type-and-copy.md) L01의 대상에서 `HOME_OUTING`을 뺀다 |
| 작성자 | Claude |
| 작성일 | 2026-11-23 |

## 1. 목적 / 배경

보호자가 위듀케어 홈을 열 때마다 어르신에게 「○○ 님이 내 위치를 확인했어요.」(L01)가 간다. 홈 카드는 좌표가 아니라 외출 여부만 보여 주는데, 앱을 열 때마다 불려 어르신이 같은 알림을 반복해서 받는다. 2026-11-23 사용자(제품 담당) 결정으로 홈 진입은 알림에서 뺀다.

## 2. 범위

### In scope
- `HOME_OUTING` 열람은 `location_access_log`에 기록하고 즉시 통보하지 않는다
- 서버 시작 시 요약(L02) 대상 조회와 선점에서 `HOME_OUTING`을 뺀다

### Out of scope
- 다른 네 경로(`REST_LAST`, `REST_TRAIL`, `REST_FAMILY`, `WS_SUBSCRIBE`)의 즉시 통보는 그대로다
- 열람 기록 조회 API, 관리자 CSV는 바꾸지 않는다. 홈 열람도 계속 나오고 `notified_at`만 비어 있다

## 3. 처리 흐름

- `LocationAccessLogService.record`: 저장 뒤 `path == HOME_OUTING`이면 통보 없이 반환한다. `notified_at`은 null로 남는다
- `LocationAccessLogRepository.findSeniorIdsWithUnnotified`, `claimUnnotified`: `path <> HOME_OUTING` 조건을 더한다. 빼지 않으면 재시작 때마다 쌓인 홈 열람이 요약으로 한꺼번에 나간다
- 이 변경 이전에 쌓인 `HOME_OUTING` 미통보 행도 요약에서 빠진다. LLD-0130 이후 홈 행은 즉시 통보되어 `notified_at`이 채워져 있으므로 남은 것은 그 이전 옛 행뿐이다

## 4. 인수조건 (Acceptance Criteria)

- [ ] 보호자가 홈을 열면 `HOME_OUTING` 행이 남고 FCM이 접수되지 않으며 `notified_at`이 null이다
- [ ] 홈 열람만 미통보로 남은 어르신은 요약 대상이 아니고, 선점 UPDATE는 `HOME_OUTING` 행을 건드리지 않는다
- [ ] 다른 경로의 열람은 이전처럼 열람마다 L01이 접수된다
- [ ] 관련 테스트(`LocationAccessLogServiceTest`, `LocationAccessLogRepositoryTest`)가 통과한다

## 5. 법적 확인

- ADR-0036은 외출 여부도 위치에서 파생된다는 이유로 통보 대상에 넣었다. 홈 카드의 외출 여부 제공은 위치정보법 제19조③의 매회 통보 대상이 아니라는 확인을 2026-11-23 사용자가 전달했다. 회신 문서와 회신 기관은 저장소에 없다
- 판단이 바뀌어 원래 동작으로 되돌릴 때는 세 곳을 함께 되돌린다. `LocationAccessLogService.record`의 `HOME_OUTING` 조기 반환, `LocationAccessLogRepository.findSeniorIdsWithUnnotified`와 `claimUnnotified`의 `path <> HOME_OUTING` 조건. 저장소 조건을 남기면 그동안 쌓인 홈 미통보 행이 요약에서 계속 빠진다. 되돌린 뒤에는 홈 행만 남은 어르신과 다른 경로 행이 섞인 어르신 모두 서버 시작 시 요약을 받는다
