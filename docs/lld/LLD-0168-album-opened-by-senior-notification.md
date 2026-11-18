# LLD-0168: 어르신 게시물 해금 알림(A08)

> Low-Level Design. 이 문서는 #1112 구현과 PR 본문의 오라클이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1112 |
| 관련 ADR | ADR-0037 |
| 작성자 | Claude / feature-1112 |
| 작성일 | 2026-11-18 |

## 1. 목적 / 배경

위듀(어르신) 알림 정책서 v12 1절 앨범 표는 어르신이 포인트로 새 게시물을 열었을 때 본인에게 「새 게시물을 열었어요.」 / 「{포인트}P를 사용했어요.」를 보내도록 정했다(10-28 확정). 칩은 앨범, 전달은 푸시+센터, 누르면 그 게시물이다. 서버에는 이 알림이 없었고, 포인트 사용 알림(P02)이 기타 칩, 센터 전용으로 대신 남았다. P02는 #1108([LLD-0069](LLD-0069-goal-achieved-and-point-center.md) 머리말)에서 껐다.

정책서 12절은 잠금 해제 API 응답 문구 「앨범이 해금되었습니다.」도 「해금」이 게임 말투라 「새 게시물을 열었어요.」로 바꾸도록 정했다.

## 2. 범위

### In scope

- 변경 모듈: widyu-domain(`NotificationType`), widyu-api(`AlbumNotificationListener`, `NotificationCopy`, `AlbumUnlockResponse`, `AlbumDocs`)
- 새 알림 종류 `ALBUM_OPENED_BY_SENIOR`, 문구 코드 `A08`
- 잠금 해제 응답 `data.message` 문구

### Out of scope

- 게시물을 쓴 보호자에게 가는 A06은 바꾸지 않는다. A06-Z와 A07이 겹치는 문제는 #1115(앨범 수신자 정렬)에서 다룬다.
- 잠금 해제 성공 응답 봉투의 `message`(`ALBM_2008` 「앨범 해금이 완료되었습니다.」)는 정책서가 따로 적지 않아 그대로 둔다.
- 해금 가격은 지금처럼 `Album.UNLOCK_PRICE`(50) 하나다. 게시물마다 다른 가격은 없다.

## 3. 인터페이스 / API

HTTP 경로, 요청은 바뀌지 않는다.

`POST /api/v1/albums/{albumId}/unlock` 응답 `data.message`: 「앨범이 해금되었습니다.」 → 「새 게시물을 열었어요.」.

알림 종류 레지스트리

| type | 칩 | 전달 | 우선순위 | 보관 | 설정 | 앱 안 표시 | 딥링크 | 문구 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `ALBUM_OPENED_BY_SENIOR` | `ALBUM` | `PUSH_AND_CENTER` | `INTERACTION` | `ROUTINE_90D` | `GENERAL`(시니어는 `forSenior(ALBUM)`) | `BANNER` | `/album/post?postId={entityId}` | A08 |

문구 A08: 제목 「새 게시물을 열었어요.」, 본문 「{포인트}P를 사용했어요.」. FCM data는 기존 타입 알림과 같다(`notificationType`, `eventId`, `deepLink`, `entityId`, `notificationId`). `seniorId`, `actorDisplayName`은 싣지 않는다(본인 알림).

우선순위: 정책서 우선순위 표는 조용히(`PASSIVE`)에 업로드 완료와 걷기 미달만 두고, 새 게시물, 댓글 같은 앨범 알림을 보통으로 둔다. 해금은 어르신이 방금 한 행동의 결과라 보통으로 둔다.

## 4. 데이터 모델

스키마 변경 없음. `fcm_notification.type`, `fcm_outbox.notification_type`은 VARCHAR라 새 enum 값에 ALTER가 필요 없다([LLD-0155](LLD-0155-enum-columns-to-varchar.md)).

## 5. 처리 흐름

1. `AlbumUnlockService.unlockAlbum`이 포인트 차감, 해제 기록 저장 뒤 `AlbumUnlockedEvent(albumId, seniorId)`를 발행한다(기존).
2. `AlbumNotificationListener.handleAlbumUnlocked`(동기 `@EventListener`, 같은 트랜잭션)가 보호자에게 A06을 접수한 뒤, 어르신 본인에게 A08을 접수한다.
   - `포인트` = `Album.UNLOCK_PRICE`. 이벤트에 금액이 없고 해금 가격은 상수 하나다.
   - `eventId` = `A08:{albumId}:{seniorId}`. 해제 기록은 (게시물, 회원) 유일이라 한 번만 생기며, 다시 접수돼도 센터 행은 하나다.
   - `entityId` = albumId, 딥링크는 타입 템플릿(위듀 앱 게시물 경로), 이미지는 게시물 작성자 프로필.
   - `relatedMemberId`는 없다(본인 알림이라 가족 확인을 하지 않는다).
3. 접수는 `FcmOutboxService.enqueue`가 센터 행과 기기별 outbox를 만들고 커밋 뒤 전송한다(기존).

## 6. 예외 / 에러 처리

| 상황 | 동작 |
| --- | --- |
| 잠금 해제 실패(포인트 부족, 이미 해제, 가족 아님 등) | 이벤트가 발행되지 않아 A08도 없다. |
| A06 남은 수 조회 DB 오류 | 기존대로 잠금 해제 트랜잭션 전체가 실패하고 A08도 없다. |
| A08 접수 실패 | 같은 트랜잭션이라 잠금 해제도 롤백된다(A06과 같은 기존 성질). |

새 오류 코드는 없다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 잠금 해제 1건에 어르신 본인에게 `ALBUM_OPENED_BY_SENIOR` 1건을 접수한다. 제목 「새 게시물을 열었어요.」, 본문 「50P를 사용했어요.」, `eventId=A08:{albumId}:{seniorId}`, 딥링크 `/album/post?postId={albumId}`.
- [x] 타입은 `PUSH_AND_CENTER`, 칩 `ALBUM`, `INTERACTION`이다.
- [x] 보호자 A06은 그대로다.
- [x] 잠금 해제 응답 `data.message`는 「새 게시물을 열었어요.」이고 Swagger 예시도 같다.
- [x] `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 스키마 변경 없음.
- 앱: 위듀 앱에 새 `notificationType` 값 `ALBUM_OPENED_BY_SENIOR`가 온다. 모르는 타입을 어떻게 그리는지는 앱 확인이 필요하다. 탭 이동은 `data.deepLink`로 된다.

## 9. 미결정 사항 (Open Questions)

- 타입 이름 `ALBUM_OPENED_BY_SENIOR`와 문구 코드 `A08`은 계획서 제안이다. 정책서 표에는 코드가 「(새)」로만 적혀 있다.

## 10. 참고

- 위듀(어르신) 알림 정책서 v12 1절 앨범 표, 9절, 12절(Confluence SCRUM 142213121)
- [LLD-0066](LLD-0066-delivery-mode-alignment-album-health-walk.md), [LLD-0069](LLD-0069-goal-achieved-and-point-center.md), [LLD-0117](LLD-0117-notification-list-tabs.md)
