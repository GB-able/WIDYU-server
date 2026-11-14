# LLD-0151: 앨범 좋아요·댓글 수를 원자 UPDATE로 저장한다

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #811 |
| 관련 ADR | - |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

좋아요·댓글 요청은 앨범을 읽어 `likeCount`·`commentCount`를 메모리에서 ±1 한 뒤 변경 감지로 저장했다. `Album`에 `@DynamicUpdate`가 없어 Hibernate는 읽어 둔 값으로 행 전체를 UPDATE한다. 그래서 두 가지가 깨진다.

1. 좋아요가 앨범을 읽은 뒤 작성자의 삭제가 먼저 커밋되면, 좋아요 커밋이 `status=ACTIVE`로 덮어 삭제된 앨범이 되살아난다. 같은 방식으로 캡션 수정, 원자 UPDATE로 올린 `view_count`(LLD-0099)도 옛 값으로 돌아간다.
2. 같은 앨범에 동시에 좋아요하면 둘 다 같은 값을 읽고 +1 해 증가가 사라진다. 홈 추천 정렬(`likeCount * 3 + commentCount * 2`)도 이 값을 쓴다.

반대 방향도 같다. 삭제와 캡션 수정도 행 전체를 쓰므로, 그 사이 커밋된 좋아요·댓글·조회 수를 읽어 둔 옛 값으로 되돌린다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api, widyu-domain
- `AlbumRepository`에 좋아요·댓글 수 원자 UPDATE 4개(조회 수 `incrementViewCount`와 같은 방식)
- `AlbumLikeService.likeAlbum`·`unlikeAlbum`, `AlbumCommentService.createComment`·`deleteComment`가 엔티티 대신 위 UPDATE를 부른다
- `Album`의 메모리 카운터 메서드 4개(`increment/decrementLikeCount`, `increment/decrementCommentCount`)를 지운다
- `Album`에 `@DynamicUpdate`를 붙여 삭제·캡션 수정·영상 처리 완료가 바뀐 컬럼만 쓰게 한다
- 이미 어긋난 값을 실제 행 수로 맞추는 일회성 SQL `scripts/mysql/fix_album_like_comment_count.sql`(파일만, 실행하지 않음)
- 실제 MySQL 재현 테스트 `AlbumCounterConcurrentMySqlTest`

### Out of scope
- 삭제된 앨범에 대한 좋아요·댓글 요청을 실패로 바꾸는 정책 변경(9절)
- 조회 수 보정(LLD-0099 9절), 댓글 좋아요 수(`album_comment.like_count`, 갱신 코드 없음)
- 상세 조회의 조회 기록 INSERT 뒤 조회 수 UPDATE가 같은 앨범의 동시 첫 조회끼리 외래키 공유 잠금으로 교착할 수 있는지(5절의 같은 패턴). 이 PR에서는 재현하지 않았다

## 3. 인터페이스 / API

HTTP 계약 변경 없음. 좋아요·좋아요 취소는 응답 본문이 없고, 댓글 작성 응답(`AlbumCommentResponse`)은 앨범 카운터를 싣지 않으므로 엔티티 메모리 값을 맞출 필요가 없다.

## 4. 데이터 모델

스키마 변경 없음.

| 메서드 (`AlbumRepository`) | JPQL |
| --- | --- |
| `incrementLikeCount(albumId)` | `likeCount = likeCount + 1` |
| `decrementLikeCount(albumId)` | `likeCount = GREATEST(likeCount - 1, 0)` |
| `incrementCommentCount(albumId)` | `commentCount = commentCount + 1` |
| `decrementCommentCount(albumId, amount)` | `commentCount = GREATEST(commentCount - amount, 0)` |

모두 `WHERE a.id = :albumId`이며 상태 조건이 없다(6절).

## 5. 처리 흐름

좋아요(`likeAlbum`, 한 트랜잭션):
1. 앨범을 ACTIVE 조건으로 읽고 가족 권한, 잠금 해제를 확인한다(기존과 같음)
2. 이미 좋아요했는지 확인한다
3. `UPDATE album SET like_count = like_count + 1` — 앨범 행 배타 잠금
4. `album_like` INSERT
5. `AlbumLikedEvent` 발행

댓글 작성은 3·4가 `comment_count` 증가와 `album_comment` INSERT다. 좋아요 취소는 `album_like` DELETE 뒤 감소, 댓글 삭제는 댓글(활성 답글 포함) `status=DELETED` 뒤 지운 개수만큼 한 번에 감소한다.

**UPDATE를 INSERT보다 먼저 하는 이유**: InnoDB는 자식 행 INSERT의 외래키 검사로 부모(`album`) 행에 공유 잠금을 건다. INSERT 뒤에 UPDATE하면 같은 앨범에 동시에 좋아요한 두 트랜잭션이 서로의 공유 잠금 때문에 배타 잠금을 얻지 못해 교착한다(#1025와 같은 패턴). UPDATE로 배타 잠금을 먼저 잡으면 같은 앨범의 좋아요·댓글은 앨범 행 잠금 순서대로 한 건씩 커밋된다. 삭제(DELETE, 상태 UPDATE)는 부모 행을 잠그지 않으므로 순서를 바꾸지 않았다.

영속성 컨텍스트: 서비스는 읽은 `Album` 엔티티의 필드를 바꾸지 않으므로 커밋 때 앨범 행 UPDATE가 나가지 않는다. 벌크 UPDATE 뒤 메모리의 카운터는 옛 값이지만 이 트랜잭션에서 읽거나 응답에 쓰지 않는다. `@DynamicUpdate`도 붙였으므로 이후 같은 트랜잭션에서 다른 필드를 바꾸더라도 카운터 컬럼은 쓰지 않는다.

선택 이유:
- **원자 UPDATE + `@DynamicUpdate`(채택)**: DB가 현재 값에 더하므로 증가가 유실되지 않고, 카운터 UPDATE는 `status`·`content`를 건드리지 않는다. `@DynamicUpdate`는 반대 방향(삭제·캡션 수정이 옛 카운터로 덮음)을 막는다. 조회 수(LLD-0099)와 같은 방식이고, `Incident`가 이미 `@DynamicUpdate`를 쓴다.
- **`@DynamicUpdate`만(기각)**: 상태 덮어쓰기는 막지만 두 좋아요가 같은 값을 읽어 같은 값을 쓰는 증가 유실은 남는다.
- **`@Version` 낙관적 잠금(기각)**: 동시 좋아요 중 하나가 실패하므로 재시도 로직이 필요하고, 인기 앨범에서 실패가 잦다. 조회 수 원자 UPDATE와도 버전이 맞지 않는다.
- **비관적 잠금으로 앨범을 읽기(기각)**: 정확하지만 권한 확인 동안에도 행을 잡고, 행 전체 UPDATE는 그대로라 `@DynamicUpdate`가 여전히 필요하다.

## 6. 예외 / 에러 처리

새 에러 코드 없음. 기존 흐름이 그대로다.

- 좋아요·댓글 작성은 시작할 때 앨범을 `status=ACTIVE`로 읽어 없으면 `ALBUM_NOT_FOUND`(404)다. 처리 중 삭제가 커밋된 경우는 이 확인을 이미 통과했으므로 요청은 성공하고 카운터와 좋아요·댓글 행이 함께 늘어난다. 앨범은 `DELETED`로 남는다.
- 원자 UPDATE에 `status = ACTIVE` 조건을 넣지 않았다. 넣으면 처리 중 삭제된 앨범에서 카운터만 안 오르고 `album_like` 행은 남아 행 수와 카운터가 어긋난다. 삭제된 앨범은 피드·상세에서 보이지 않으므로 카운터가 오르는 것은 사용자에게 드러나지 않는다(9절 기본값).
- 좋아요 취소·댓글 삭제의 감소는 0 아래로 내려가지 않는다(`GREATEST`). 기존 엔티티 메서드의 `> 0` 확인과 같다.
- 같은 회원의 동시 좋아요가 `album_like` UK에 걸리면 트랜잭션이 롤백되어 앞서 올린 카운터도 함께 되돌아간다.

## 7. 인수조건 (Acceptance Criteria)

- [x] AC1: 서로 다른 회원 10명이 같은 앨범을 읽은 뒤 동시에 좋아요하면 `like_count`가 10, `album_like` 행이 10이다 (`AlbumCounterConcurrentMySqlTest`)
- [x] AC2: 같은 조건의 댓글 10건 뒤 `comment_count`가 10이다 (`AlbumCounterConcurrentMySqlTest`)
- [x] AC3: 좋아요가 앨범을 읽은 뒤 삭제가 먼저 커밋되면 앨범은 `DELETED`로 남는다 (`AlbumCounterConcurrentMySqlTest`)
- [x] AC4: 댓글이 앨범을 읽은 뒤 삭제가 먼저 커밋되면 앨범은 `DELETED`로 남는다 (`AlbumCounterConcurrentMySqlTest`)
- [x] AC5: 삭제가 앨범을 읽은 뒤 좋아요가 먼저 커밋되면 삭제 뒤에도 `like_count`가 1이다 (`AlbumCounterConcurrentMySqlTest`)
- [x] AC6: 좋아요·취소·댓글 작성·삭제가 리포지토리의 원자 UPDATE를 부르고, 실패 경로는 부르지 않는다 (`AlbumLikeServiceTest`, `AlbumCommentServiceTest`)
- [x] AC7: 수정 전 코드에서 AC1~AC5가 실패한다
- [ ] AC8: `./gradlew :backend:widyu-api:test`가 통과한다

검증 기록은 PR 본문에 둔다. MySQL 테스트는 `ALBUM_MYSQL_URL`이 있을 때만 돌고 CI에서는 건너뛴다.

## 8. 영향 범위 / 마이그레이션

- DB 스키마 변경 없음
- `Album`의 모든 UPDATE가 바뀐 컬럼만 쓴다(`@DynamicUpdate`). 삭제는 `status`·`updated_at`, 캡션 수정은 `content`·`updated_at`
- 카운터 원자 UPDATE는 JPQL 벌크 UPDATE라 `album.updated_at`을 바꾸지 않는다. 기존에는 좋아요·댓글이 `updated_at`을 갱신했다. `Album.updatedAt`을 읽는 코드는 없다
- 이미 어긋난 값은 배포 뒤 `scripts/mysql/fix_album_like_comment_count.sql`로 맞춘다. `like_count`는 `album_like` 행 수, `comment_count`는 ACTIVE `album_comment` 행 수다. 개발 서버, 운영 실행은 이 PR 범위가 아니다

## 9. 미결정 사항 (Open Questions)

- [ ] 처리 중 삭제된 앨범에 대한 좋아요·댓글 요청을 실패(404)로 돌려줄지. 기본값으로 지금 동작(시작 시점에 ACTIVE면 성공, 카운터와 행이 함께 늘고 앨범은 `DELETED` 유지)을 유지했다. 실패로 바꾸려면 원자 UPDATE에 상태 조건을 넣고 갱신 0건이면 `ALBUM_NOT_FOUND`를 던지면 된다. 확인 필요
- [ ] 보정 SQL의 실행 시점(개발 서버, 운영 구축 시)과 백업 방법

## 10. 참고

- [LLD-0099](LLD-0099-album-view-count-atomic-update.md) 조회 수 원자 UPDATE
- #1025 앨범 잠금 해제의 외래키 공유 잠금 교착(같은 잠금 순서 문제)
