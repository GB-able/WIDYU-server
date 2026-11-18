# LLD-0066: 앨범·건강일정 임박·걷기 미달 알림 전달 방식 정합

> 이 문서는 이슈 #701 구현·검수의 기준이다. W2 LLD-0060의 타입/문구 레지스트리와 W3 LLD-0062의 수신자×이벤트 센터 행을 전제로 한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #701 |
| 관련 ADR | ADR-0037 결정 1·2 |
| 작성자 | Codex / feature/701 |
| 작성일 | 2026-10-02 |

## 1. 목적 / 배경

현재 앨범·건강일정·걷기 리스너는 type 없는 `FcmSendDto`를 보내므로 승인된 전달 방식을 적용하지 못한다. 업로드 완료·걷기 미달이 센터에 저장되고, 좋아요는 푸시로 나가며, 건강일정 잠금화면 제목에 일정명이 드러난다. 각 호출이 `NotificationType`, 문구표 v0.4, 역할별 목적지와 데이터 필드를 사용하도록 설계한다.

## 2. 범위

### In scope

- `widyu-api`: `AlbumNotificationListener`, `HealthScheduleNotificationListener`, `WalkNotificationListener`, `AlbumUnlockRepository`; A04 식별에 필요한 `AlbumCommentService`·`AlbumCommentedEvent`.
- H01 푸시와 센터의 문구 분리 및 A06 필드 보존에 필요한 `FcmSendDto`, `FcmOutboxService`; `widyu-domain`의 `FcmNotification` nullable 필드 2개와 운영 DDL·ERD. W4(#703, LLD-0067) 위 stack에서 알림센터 응답 DTO에도 두 필드를 반영한다.
- E1·E4·E5·E14·E17과 같은 리스너의 A02·A03·A04·A07 역할·문구·딥링크 정합.

### Out of scope

- H02~H04 보호자 건강일정 변경, W02 목표 변경, 복약·안전, 공통 알림센터 목록/읽음 API 신설, 걷기 측정 품질 정책 신설.
- `ALBUM_CONTENT_STOCK_DECREASED`, `ALBUM_INACTIVITY_NUDGE` 신규 알림. 기존 3/5/7일 비활성 스케줄은 type 없는 레거시 경로로 그대로 유지한다. 발송 지속 여부는 hemlo 확인 대기이며 코디네이터가 회신 초안 §6에 질문을 추가한다. 확인되면 별도 1줄 PR로 처리한다. HEMLO-REVIEW §4.3의 마지막 업로드 4일 경과·재고 감소 별도 알림도 만들지 않는다.
- `FcmCategory` 추가, W3의 공통 멱등·재시도 정책 변경.

## 3. 인터페이스 / API

HTTP 경로·요청·응답 래퍼는 바뀌지 않는다. 입력은 앨범 도메인 이벤트와 기존 시간별/19시 스케줄러, 출력은 `FcmService.sendMessageToUser(recipientId, FcmSendDto)`다. `NotificationType`이 `PUSH_ONLY`(outbox만), `CENTER_ONLY`(센터 행만), `PUSH_AND_CENTER`(센터 행 1건과 기기별 outbox)를 결정한다. `eventId`는 수신자×이벤트 한 키로 재시도에 유지한다. 센터 미저장 푸시는 FCM data에 `eventId`가 있고 `notificationId`는 없다.

| type | 수신자 | 방식·문구 | 딥링크·필드 |
| --- | --- | --- | --- |
| `ALBUM_UPLOAD_COMPLETE` | 업로더 | `PUSH_AND_CENTER` A01(#1106 전에는 `PUSH_ONLY`), `PASSIVE`, `foregroundPresentation=NONE`, `eventId=A01:{albumId}` | 업로더 역할별 딥링크: GUARDIAN은 `/post?postId={albumId}`, SENIOR는 `widyu://albums/{albumId}`; `entityId=albumId` |
| `ALBUM_CREATED` | 반대 역할 가족, 보호자 게시물이면 같은 가족의 다른 보호자도(#1115) | `PUSH_AND_CENTER` A02-S(시니어 수신)/A02-C(보호자 수신) | 수신 앱의 게시물 상세, `entityId=albumId`; 보호자 수신이면 `seniorId` |
| `ALBUM_COMMENTED` / `ALBUM_REPLIED` | A03은 게시물 작성자, A04는 원댓글 작성자와 게시물 작성자(같은 사람이면 한 번, #1022). 본인 반응 제외 | `PUSH_AND_CENTER` A03/A04 | 해당 댓글·답글, `entityId=albumId`, `data.commentId=저장된 댓글/답글 ID` |
| `ALBUM_LIKED` | 게시물 작성자, 본인 반응 제외 | `PUSH_AND_CENTER` A05(#1106 전에는 `CENTER_ONLY`, 설정 `NONE`), `INTERACTION`, 설정 `GENERAL`, `foregroundPresentation=BANNER` | 해당 게시물, `entityId=albumId` |
| `ALBUM_UNLOCKED` | 게시물을 올린 보호자 한 명. A06-Z는 이 게시물만 보면 모두 본 상태면 보내지 않음(#1115) | `PUSH_AND_CENTER` A06-L(남음)/A06-Z(0) | 위듀케어 해당 게시물, `entityId=albumId`, `seniorId`, `seniorDisplayName`, `remainingLockedCount`(0 포함) |
| `ALBUM_ALL_VIEWED` | 작성자 GUARDIAN·독자 SENIOR 조합의 보호자 작성자만 | `PUSH_AND_CENTER` A07 | 총수·읽은 수를 ACTIVE 기준으로 일치, `/album`, `seniorId=읽은 시니어` |
| `HEALTH_SCHEDULE_UPCOMING` | 시니어 일정 소유자만 | `PUSH_AND_CENTER` H01-S의 `-OS`/`-INAPP` | 시니어 일정 상세, `entityId=scheduleId`; 보호자 본인 일정은 [LLD-0076](LLD-0076-guardian-deeplink-paths.md)에 따라 발행하지 않음 |
| `WALK_GOAL_UNMET` | 19시 목표 미달인 시니어(1걸음 이상, 0걸음과 기록 없음은 보내지 않음, #1120) | `PUSH_AND_CENTER` W01(#1106 전에는 `PUSH_ONLY`), `eventId=W01:{memberId}:{date}` | 걷기 목표, 실제 걸음 수 |

앨범 시니어 딥링크는 W2 제안 `widyu://albums/{entityId}`를 유지한다. 보호자 A01/A02/A05는 `/post?postId={albumId}`, A03/A04는 `/post?postId={albumId}&commentId={commentId}`, A06은 `/post?postId={entityId}`, A07은 `/album`을 사용한다. 보호자 역할별 경로는 `GuardianDeepLinks`가 만들고 A06/A07은 `NotificationType` 템플릿을 사용한다([LLD-0076](LLD-0076-guardian-deeplink-paths.md)). H01 보호자 본인 일정은 서버 미사용이며 W01은 `widyu://walk/goal`을 유지한다. `FcmCategory`는 기존 ALBUM/HEALTH_SCHEDULE/WALK를 사용한다.

`GET /api/v1/notifications`의 `items`는 저장된 센터 행에서 다음 응답 필드를 그대로 반환한다. `ALBUM_UNLOCKED` 외의 행은 저장값이 없으면 null이며, 레거시 `type IS NULL` 행에도 같은 규칙을 적용한다.

| 응답 필드 | 타입 | A06 값 | 값이 없는 행 |
| --- | --- | --- | --- |
| `seniorDisplayName` | nullable String | 잠금 해제한 시니어 표시명 | null |
| `remainingLockedCount` | nullable Integer | 해금 직후 남은 잠금 수, 0 포함 | null |

## 4. 데이터 모델

- `AlbumUnlockRepository`에 `countRemainingLockedByWriterAndSenior(writerId, seniorId, justUnlockedAlbumId)`를 추가한다. `Album.status=ACTIVE`, `Album.member.id=writerId`, `Album.id<>justUnlockedAlbumId`, `NOT EXISTS (AlbumUnlock where album=a and member.id=seniorId)`를 센다. 방금 해제한 게시물을 제외하므로 미커밋 unlock 행을 조회하지 않아도 잠금 해제 직후 수가 된다. 다른 작성자, PROCESSING/비활성/삭제, 이미 해제한 게시물은 세지 않는다.
- `AlbumCommentedEvent`에 저장된 `commentId`와 답글 여부(예: `parentCommentId`)를 더한다. 현재 세 필드(`albumId`, `commenterMemberId`, `albumAuthorId`)로는 답글 구분과 댓글 딥링크가 불가능하다. `AlbumCommentService`는 저장된 댓글/답글에서 값을 가져온다.
- `FcmSendDto`에 nullable `centerTitle`, `centerBody`, `seniorDisplayName`, `remainingLockedCount`를 둔다. H01 `title/content`는 OS 문구, `centerTitle/centerBody`는 INAPP 문구다. outbox는 OS 제목·본문을 보존하고 FCM data에 앱 안 문구를 함께 넣어 재시도에서도 유지한다. 센터 전용 문구가 없으면 기존 제목·본문을 사용한다.
- `FcmNotification`에 nullable `senior_display_name VARCHAR(255)`와 `remaining_locked_count INT`를 추가한다. `FcmOutboxService.enqueue`가 A06 센터 행에 저장한다. 두 필드의 응답 노출은 W4 위 stack에서 반영한다. FCM data에도 두 값을 문자열로 보내며 **0은 생략하지 않는다.** 과거 행은 NULL이며 소급 계산하지 않는다. enum 컬럼 신설은 없다.

## 5. 처리 흐름

1. `AlbumCreatedEvent`에서 업로더에게 A01을 한 번 enqueue하고, 가족 중 반대 역할 수신자마다 A02-S/C를 보낸다. 문구는 `NotificationCopy.of(type, code, values)`를 사용한다. 영상은 기존 업로드 완료 이벤트 시점을 유지한다.
2. 댓글 저장 트랜잭션에서 ID와 답글 여부를 이벤트로 발행한다. 동기 `@EventListener`는 A03/A04 type을 고르고, 좋아요는 A05 type으로 푸시와 센터에 넣는다(#1106). 본인 댓글·좋아요에는 알림이 없다.
3. 잠금 해제 기록 저장 뒤 같은 트랜잭션에서 `AlbumUnlockRepository.countRemainingLockedByWriterAndSenior(...)`를 한 번 호출하고 원 작성자인 보호자 한 명에게 A06을 만든다. 남은 수가 1 이상이면 A06-L, 0이면 A06-Z다. 쿼리는 방금 해제한 albumId를 제외한다. DB 오류 시 잠금 해제 트랜잭션도 실패하며 별도 읽기 빈·대체 문구 분기는 두지 않는다.
4. A07은 작성자 GUARDIAN·독자 SENIOR 조합일 때만 해당 보호자 작성자에게 보낸다. 다른 역할 조합은 조기 반환한다. A06 남은 수 0으로 A07을 자동 생성하지 않는다. 기존 `countByMemberId`가 비활성 게시물을 포함하므로 판정에 쓰는 총수·읽은 수를 모두 ACTIVE 기준으로 맞춘다.
5. H01은 기존 `UPCOMING` 일정 시간 범위의 시니어 소유자에게만 간다. 보호자 **본인** 일정의 H01-C-SELF는 [LLD-0076](LLD-0076-guardian-deeplink-paths.md)에 따라 발행하지 않는다. `H01-C-SENIOR`는 보호자가 시니어 일정을 확인 책임자로 받는 별도 수신 경로가 현재 없어 생성하지 않는다. OS 제목·본문에는 일정명·시각·주소를 넣지 않고, INAPP 센터·앱 안 문구에만 오전/오후 시각과 일정명을 넣는다.
6. W01은 19시 기준 당일 Walk 행이 있고 `actualSteps>0`, `actualSteps<goalSteps`일 때 한 건 보낸다. Walk 행 없음·목표 없음·0·음수·달성은 만들지 않는다. 현재 모델에 품질·출처 필드가 없어 양수 동기화값은 신뢰 가능한 값으로 간주한다. 상한 임계값은 두지 않는다(§9). W01 단일 문구를 쓴다.

리스너는 기존 동기 `@EventListener`·스케줄러를 사용한다. FCM HTTP 호출은 W3 outbox dispatcher가 커밋 뒤 수행한다. 새 `@Async`가 필요하면 별도 빈과 `@Transactional`을 함께 적용한다.

> 2026-11-18 갱신(#1107): H01 앱 안 문구의 시각은 정책서 공통 규칙 3대로 「오전/오후 N시 M분」(예: 「오후 3시 30분」)이다. 정각은 분을 빼고 「오전 9시」로 쓴다(사용자 결정 2026-11-18). 일정명은 공통 규칙 2대로 15글자를 넘으면 앞 15글자 + 「…」이며, 글자는 사람이 보는 글자(확장 그래핌, LLD-0167과 같은 기준)로 센다. 자르기는 `NotificationCopy`가 `{일정명}` 값을 채울 때 하므로 H01-S-INAPP, H01-C-SENIOR-INAPP에 같이 적용된다.

> 2026-11-18 갱신(#1109): 알림 정책서(위듀케어 v18)에 따라 `H01-C-SENIOR`를 만든다. 위 5절의 「H01-C-SENIOR는 생성하지 않는다」를 대신한다.
>
> - 언제, 누구에게: 어르신 일정 H01과 같은 회차(LLD-0105의 10분 주기, 일정 60~70분 전)에 그 어르신 가족(`SeniorProfile.family`)의 보호자 각각에게 1건. 가족이 없으면 보내지 않는다. 보호자 본인 일정(H01-C-SELF)은 지금처럼 보내지 않는다.
> - 문구: 잠금화면(`title/content`) H01-C-SENIOR-OS 「{시니어 이름} 님의 건강 일정이 곧 있어요.」 / 「앱에서 일정 시간과 내용을 확인해주세요.」. 센터와 `data.inAppTitle/inAppBody`는 H01-C-SENIOR-INAPP 「{오전/오후 시각}에 {시니어 이름} 님의 일정이 있어요.」 / 「{일정명} 일정을 확인해보세요.」. 시니어 이름이 비면 「가족」.
> - 전달: 타입은 어르신과 같은 `HEALTH_SCHEDULE_UPCOMING`(목표 칩, `PUSH_AND_CENTER`, INTERACTION, 딥링크 `/goal`). `seniorId`, `entityId=scheduleId`, `relatedMemberId=어르신`을 싣는다. 접수 단계가 같은 가족, 활성 회원인지 다시 확인한다.
> - 수신 스위치: 정책서의 보호자 「건강일정 임박」 단독 스위치는 아직 없다. 타입의 설정 그룹 `GENERAL`을 따르며, 끄면 푸시만 빠지고 센터 행은 남는다(ADR-0037 결정 5 현행). 단독 스위치와 「끄면 센터에도 안 남김」은 보호자 스위치 재편 결정 뒤에 다룬다.
> - 중복: eventId는 `H01C:{scheduleId}:{scheduledAt}`의 이름 기반 UUID라 같은 회차를 다시 실행해도 보호자의 센터 행은 1건이다(outbox 푸시는 실행마다 생길 수 있어 어르신 H01과 같은 성질). 일정 시각을 바꿔 다시 창에 들면 새 알림이다.
> - 실패 격리: 보호자 한 명의 접수가 실패하면 로그를 남기고 다음 보호자로 간다.
> - 인수조건: 가족 보호자 둘인 어르신 일정은 어르신 1건 + 보호자마다 1건, 잠금화면 문구에 일정명, 시각이 없고, 두 번 실행해도 보호자 센터 행이 1건, `GENERAL`을 끈 보호자는 센터 행이 남고 푸시가 빠진다(`HealthScheduleNotificationListenerTest`, `FcmSchedulerOutboxIntegrationTest`).

## 6. 예외 / 에러 처리

| 상황 | 동작 |
| --- | --- |
| A06 남은 수 조회 DB 오류 | 같은 잠금 해제 트랜잭션을 실패시킨다. A06-FALLBACK을 발행하지 않는다. |
| A06 시니어 이름 부재 | 문구표의 이름 대체 규칙을 적용하고 `seniorDisplayName`은 null로 둔다. |
| 앨범/회원 원본 없음·가족 접근 불가 | 기존 `BusinessException`/수신자 적격성 경로를 따른다. |
| H01 일정명·시각 누락 | 엔티티 필수 값 위반을 기록하고 해당 건을 건너뛴다. OS에 상세를 넣어 대체하지 않는다. |
| W01 Walk 행 없음·목표 없음·0·음수·달성 | outbox·센터 모두 0건. 양수 동기화값 외의 신뢰 불가 기준은 §9 회신 대기다. |

새 HTTP 오류 코드는 없다. 토큰이 없거나 푸시 설정을 꺼도 `PUSH_AND_CENTER` 센터 행은 W3 정책대로 남는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] A01은 업로더에게 푸시만 보내며 보호자 업로더의 `deepLink`는 보호자 앱 게시물, 시니어 업로더의 딥링크는 시니어 앱 게시물이다. A02는 반대 역할 가족에게 A02-S/C를 역할별 딥링크로 보낸다.
- [x] 댓글·답글은 저장된 ID로 A03/A04 type·문구·댓글 딥링크를 구분한다. 본인 반응에는 알림이 없다. A05는 작성자 센터 행만 만들고 outbox·앱 안 즉시 표시가 없다.
- [x] A06 남은 수는 해당 보호자의 ACTIVE 게시물 중 해당 시니어가 아직 해제하지 않은 수와 같다. 1 이상/0이 A06-L/A06-Z로 나뉘며 `seniorDisplayName`, `remainingLockedCount`, `seniorId`, `entityId`가 FCM data·센터 행에 반영된다. DB 오류는 잠금 해제 트랜잭션을 실패시킨다.
- [x] 남은 수 0만으로 A07을 만들지 않는다. 총수·읽은 수를 ACTIVE 기준으로 맞추고 작성자 GUARDIAN·독자 SENIOR 조합에서만 보호자에게 A07 1건을 보내며, 시니어 작성자 게시물을 보호자가 모두 읽어도 0건이다.
- [x] H01은 당시 일정 당사자에게만 갔다. [LLD-0076](LLD-0076-guardian-deeplink-paths.md) 이후 보호자 본인 일정은 H01 0건이며 시니어 일정의 OS·INAPP 문구 분리는 유지한다. H01-C-SENIOR는 생성하지 않는다.
- [x] W01은 당일 `actualSteps=0`이면 0건, 양수·목표 미달이면 푸시 1건·센터 0건, 목표 달성이면 0건이다. Walk 행이 없거나 목표가 없으면 0건이다.
- [x] type별 `DeliveryMode`·문구·딥링크·`entityId`·`seniorId`·`commentId`·A06 필드를 `ArgumentCaptor<FcmSendDto>`로 확인하고 센터/outbox 여부는 상태로 확인한다. JUnit 5+Mockito, `given(...).willReturn(...)`, 한글 언더스코어 메서드명, `@DisplayName("<행위>하면 <결과>한다")`를 사용한다.
- [x] 운영 DDL·ERD를 반영하고 `bash scripts/harness/verify.sh`가 통과한다. 엔티티 변경 뒤 `./gradlew compileJava`와 Domain+API 테스트를 실행한다. H2 통과를 운영 MySQL ENUM 검증으로 간주하지 않는다.

## 8. 영향 범위 / 마이그레이션

운영 `ddl-auto=validate`에 앞서 W3 DDL 다음으로 `scripts/mysql/alter_fcm_notification_album_unlock.sql`을 적용한다. 이 스크립트는 `fcm_notification`에 `senior_display_name VARCHAR(255) NULL`과 `remaining_locked_count INT NULL`을 추가한다. 신규 `NotificationType`·`FcmCategory` 값은 없어 기존 native ENUM은 수정하지 않는다. **두 필드의 응답 노출은 W4 위 stack에서 반영한다.** H01은 outbox의 OS 문구와 센터 행의 INAPP 문구를 재시도·다중 기기에서도 유지한다. W3 위에 스택 순서대로 병합한다. 운영 MySQL에서 DDL을 실제 실행하는 검증은 배포 단계에 남는다.

## 8.1 정책서 v12/v18 맞춤 (#1106, 2026-11-18)

위듀(어르신) v12, 위듀케어(보호자) v18 알림 정책서는 사람에게 보이는 알림을 모두 「푸시+센터」로 정했다. 위 표와 §5, §7의 업로드 완료, 좋아요, 걷기 미달 항목은 이 절이 대체한다.

- A01, W01, 복약 인증 재알림 M02, M03(`MEDICATION_REMINDER_10/20`)을 `PUSH_AND_CENTER`로 바꾼다. 우선순위는 그대로다(A01, W01 `PASSIVE`, M02, M03 `INTERACTION`).
- A05를 `PUSH_AND_CENTER`로 바꾸고 설정 그룹을 `GENERAL`로 둔다(보호자는 「일반」, 시니어는 기존 `forSenior(ALBUM)` 판정). 정책서 우선순위 표는 조용히(`PASSIVE`)에 업로드 완료와 걷기 미달만 두므로 A05는 보통(`INTERACTION`), 앱 안 표시는 다른 앨범 알림과 같은 `BANNER`다.
- 다시 접수돼도 센터 행이 하나만 남도록 `eventId`를 정한다. A01 `A01:{albumId}`, W01 `W01:{memberId}:{date}`. M02, M03은 이미 `{type}:{scheduleId}:{recipientId}:{date}` 기반이다. 같은 `eventId`로 다시 접수하면 센터 행은 재사용하고 기기별 푸시는 다시 만든다(기존 enqueue 동작).
- W01 0걸음 발송(#801)은 그대로다. 정책서는 미정(추천: 안 보냄)이다.
  - 2026-11-18 갱신(#1120): 사용자 결정으로 0걸음 기록과 오늘 기록 없음은 보내지 않는다.
- 배포 순간 이미 `PUSH_ONLY`로 접수돼 미전송인 outbox 행은 `notification_id`가 없다. `FcmDelivery`는 지금까지 타입의 전달 방식이 센터 저장이면 outbox ID를 센터 ID로 대신 보냈다([LLD-0062](LLD-0062-notification-center-row-per-event.md) §5-6의 W3 이전 폴백). 그러면 이 행들이 존재하지 않거나 다른 센터 행 ID를 보내므로, 타입 있는 행은 FK가 없으면 `notificationId`를 보내지 않게 바꾼다. 폴백은 타입 없는 레거시 행에만 남는다. W3(2026-10-01) 이전에 접수된 미전송 outbox는 푸시 만료(TTL)가 지나 더 전송되지 않는다.
- 인수조건
  - [x] A01, A05, M02, M03, W01 접수 시 센터 행 1건과 기기별 outbox가 만들어진다.
  - [x] 같은 A01, W01, M02, M03 `eventId`로 두 번 접수해도 센터 행은 1건이다.
  - [x] 센터 FK 없이 접수된 타입 outbox는 전달 방식과 무관하게 `notificationId`를 보내지 않는다.

## 8.2 앨범 수신자 정렬 (#1115, 2026-11-18)

위듀케어(보호자) 알림 정책서 v18 앨범 표와 10절에 맞춘다. 위 표와 §5의 A02, A06 항목은 이 절이 대체한다.

- A02-C 수신자: 정책서는 「다른 가족이나 어르신이 게시물을 올렸을 때」다. 보호자가 올린 게시물도 같은 가족의 다른 보호자에게 A02-C(위듀케어 게시물 경로, `relatedMemberId=작성자`, `seniorId` 없음)를 보낸다. 올린 보호자 본인은 받지 않는다. 시니어에게 가는 A02-S, 시니어 게시물의 A02-C는 그대로다.
- A06-Z와 A07 중복: 시니어가 보호자의 마지막 잠긴 게시물을 열면 A06-Z가 가고, 이어서 그 게시물을 처음 조회하면 모두 본 상태가 되어 A07이 간다. 정책서 10절 추천대로 A07만 남긴다. 잠금 해제 시점에 남은 수가 0이고 `조회한 게시물 수 + 1 == 전체 ACTIVE 게시물 수`이면 A06-Z를 보내지 않는다. 해제 전 게시물은 조회로 치지 않으므로(`AlbumFacadeImpl.getAlbumDetail`) 방금 연 게시물은 아직 조회 수에 없다. 열어 두고 안 본 다른 게시물이 있으면 조회해도 A07이 가지 않으므로 A06-Z를 그대로 보낸다.
- 한계: 시니어가 마지막 게시물을 열고 조회하지 않으면(앱 종료 등) 보호자는 A06-Z도 A07도 받지 않는다.
- 인수조건
  - [x] 보호자 두 명 가족에서 한 보호자가 올리면 다른 보호자에게 A02-C 1건, 올린 보호자는 A01만 받는다.
  - [x] 남은 수 0, `조회 + 1 == 전체`이면 A06 0건(시니어 본인 A08은 그대로), 아니면 A06-Z 1건이다.

## 9. 미결정 사항 (Open Questions)

- A06 두 필드의 응답 DTO 반영은 W4 머지 뒤 후속 작업에서 W4 위 stack 작업으로 변경했다. 응답은 DB 저장값을 그대로 사용하고 0과 null을 구분한다.
- 계획 §5의 2026-10-02 임시 보호자 경로는 [LLD-0076](LLD-0076-guardian-deeplink-paths.md)에 따라 2026-10-03 FE 문서의 앱 내부 경로로 교체·확정했다. 위듀 경로는 FE 전달 대기다. H01-C-SELF 문구는 `NotificationCopy`에 정본표로 남지만 서버 미사용이다.
- 계획 §5 가정표 원문: `시니어 푸시 설정 항목 | GENERAL 하나 | 그대로 | —`. 시니어 대상 A01·A02·H01·W01에도 이 가정을 따른다.
- W01 0걸음 발송은 #801에서 보냄으로 바뀌었고, 정책서 v12는 미정(추천: 안 보냄)이다. #1106은 보냄을 유지한다. #1120에서 안 보냄으로 바꿨다.
- W01의 `신뢰 불가` 값 정의는 회신 대기다. 현재 `Walk.actualSteps`에 품질·출처 필드가 없으므로 **양수 동기화값을 신뢰**하는 작업 가정으로 구현한다(2026-10-02 코디네이터 답변). 0·Walk 행 없음은 발송하지 않으며 임의 상한·최신성 기준과 새 필드는 만들지 않는다. hemlo 회신이 다른 기준을 정하면 이 항목과 판정을 개정한다.
- A06 남은 수는 서버가 같은 트랜잭션의 단순 count 쿼리로 항상 계산 가능하므로 대체 문구 경로가 없다. DB 오류는 트랜잭션 실패로 처리한다.
- 기존 3/5/7일 앨범 비활성 알림은 type 없는 레거시 동작을 유지한다. hemlo 회신 뒤 지속 여부를 별도 PR에서 결정한다.

## 10. 참고

- ADR-0037 결정 1·2, LLD-0060, LLD-0062, `docs/erd/ERD-0001-initial-domain.md`.
- 승인판 `FE-HANDOFF-v0.4` §4 1~6·12·13·§6, `NOTIFICATION-COPY-CATALOG-v0.4` §2·§4, 기준 문서 `HEMLO-REVIEW-v0.5` §4.3·§5·§10, `FE-DELIVERY-PACKAGE-v1.2` §7·§11, `NOTIFICATION-CENTER-UX-SPEC-v0.3` §9.
- `DEVTEAM-CODE-DIFF-HIGHLIGHT-2026-09-29` E1·E4·E5·E14·E17, `BE-IMPLEMENTATION-PLAN-2026-10-01` §1·§4.6·§5.
