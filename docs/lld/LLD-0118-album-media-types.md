# LLD-0118: 앨범 피드, 상세, 캘린더 응답에 미디어별 타입 mediaTypes 추가

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #948 |
| 관련 ADR | - |
| 선행 | #945 / #813 (잠금 전 썸네일 한 장, [LLD-0021](LLD-0021-album-family-visibility.md)) |
| 작성자 | Claude |
| 작성일 | 2026-11-02 |

## 1. 목적 / 배경

앱은 썸네일 위에 영상이면 재생 아이콘을 그린다. 그런데 앨범 피드, 상세, 캘린더 응답에는 미디어별 타입이 없다.

- 피드의 `primaryMediaType`은 앨범에 영상이 하나라도 있으면 `VIDEO`다. 사진이 먼저인 앨범이면 대표 썸네일(첫 썸네일)은 사진인데 값은 `VIDEO`라 대표 썸네일의 타입과 어긋난다.
- 상세와 캘린더에는 타입 정보가 아예 없다.

## 2. 범위

### In scope
- 변경 모듈: widyu-domain(`Album` 메서드 하나, 컬럼 변경 없음), widyu-api
- `AlbumFeedResponse`, `AlbumDetailResponse`, `FamilyAlbumResponse`에 `mediaTypes` 배열 추가.

### Out of scope
- 미디어 피드(`AlbumMediaResponse`): 이미 항목마다 `type`(`image`/`video`)이 있다.
- 시니어 홈 카드: 이번 요청 범위 밖.
- 응답을 미디어 객체 배열(`media: [{type, url, thumbnailUrl}]`)로 바꾸는 구조 변경. 기존 필드를 깨는 호환성 변경이라 이번에는 나란한 배열을 추가한다.
- `primaryMediaType` 변경.

## 3. 인터페이스 / API

`mediaTypes`: `List<MediaType>`, 값은 `PHOTO` 또는 `VIDEO`(기존 `primaryMediaType`과 같은 enum).

- `mediaTypes[i]`는 `i`번째 미디어의 타입이다. 해금한 앨범에서는 `mediaUrls[i]`, `thumbnailUrls[i]`와 같은 미디어를 가리킨다.
- 잠금 전 앨범은 썸네일과 같이 첫 미디어의 타입 하나만 담는다(`thumbnailUrls`가 첫 썸네일 하나인 것과 같은 규칙, LLD-0021).
- 미디어가 없으면 빈 배열.

```json
// 잠금 전
{ "mediaUrls": [], "thumbnailUrls": ["v_thumb.jpg"], "mediaTypes": ["VIDEO"] }
// 해금 후
{ "mediaUrls": ["v.mp4", "p.jpg"], "thumbnailUrls": ["v_thumb.jpg", "p.jpg"], "mediaTypes": ["VIDEO", "PHOTO"] }
```

## 4. 데이터 모델

변경 없음. 타입은 저장하지 않고 `mediaUrls`의 확장자로 판별한다. `Album`의 기존 판별(`isVideoUrl`)과 같은 기준이며, 영상 확장자가 아니면 `PHOTO`다(`getPrimaryMediaType`과 같다).

`Album.getMediaTypes()`: `mediaUrls` 순서대로 타입 목록을 돌려준다.

## 5. 처리 흐름

세 응답의 정적 팩토리에서 잠금 여부에 따라 `album.getMediaTypes()` 전체 또는 앞 1개를 담는다.

## 6. 예외 / 에러 처리

없음.

## 7. 인수조건 (Acceptance Criteria)

- [ ] 해금한 앨범의 피드, 상세, 캘린더 응답에서 `mediaTypes`가 `mediaUrls` 순서대로 `PHOTO`/`VIDEO`를 담는다.
- [ ] 잠금 전 앨범은 세 응답 모두 `mediaTypes`에 첫 미디어의 타입 하나만 담는다.
- [ ] 사진이 먼저이고 영상이 뒤인 앨범에서 잠금 전 `mediaTypes`는 `["PHOTO"]`다(`primaryMediaType`은 `VIDEO`).
- [ ] 기존 필드 값은 바뀌지 않는다.
- [ ] `./gradlew compileJava`, album/home 테스트 통과.

## 8. 영향 범위 / 마이그레이션

- DB 변경 없음. 새 필드라 기존 앱은 무시해도 된다.
- #945가 먼저 머지되어야 한다. 이 브랜치는 #945 브랜치 위에서 만들었다.

## 9. 미결정 사항 (Open Questions)

- 없음.

## 10. 참고
- `AlbumMediaResponse`(미디어 피드의 항목별 타입), `Album.getPrimaryMediaType`
