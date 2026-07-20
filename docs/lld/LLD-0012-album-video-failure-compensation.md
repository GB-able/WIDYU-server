# LLD-0012: 앨범 영상 처리 실패 보상 삭제

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #424 |
| 관련 ADR | ADR-0010 |
| 작성자 | Codex |
| 작성일 | 2026-07-21 |

## 1. 목적 / 배경

영상 비동기 처리 중 일부 영상/썸네일 업로드가 성공한 뒤 후속 단계가 실패하면 `PROCESSING` 앨범은 `DELETED` 처리되지만 업로드 성공분 S3 파일이 남을 수 있다.
실패 앨범은 계속 숨기되, 실패 전 업로드된 S3 파일을 best-effort로 보상 삭제한다.

## 2. 범위

### In scope

- 변경 모듈: widyu-api
- `AlbumVideoProcessingService`에서 비동기 처리 실패 시 이미 수집한 영상/썸네일 URL을 삭제한다.
- `AlbumFileService.uploadAlbumVideoWithThumbnail()`에서 영상 업로드 후 썸네일 업로드가 실패하면 업로드된 영상 URL을 삭제한다.
- 삭제 실패는 warn 로그만 남기고 원래 실패 흐름을 유지한다.
- 실패 시 앨범 `DELETED` 상태 전환과 임시 파일 삭제를 유지한다.
- 성공 시에만 `AlbumCreatedEvent`를 발행한다.

### Out of scope

- 메시지 큐, outbox, 재시도 워커 도입
- 서버 재시작 시 처리 중 작업 복구
- 실패 앨범 사용자 안내 API
- 앨범/미디어 DB 스키마 변경

## 3. 인터페이스 / API

기존 API 계약 변경 없음.

- `POST /api/v1/albums/upload`
- 영상이 있는 요청은 기존처럼 202 Accepted와 `albumId`를 반환한다.

## 4. 데이터 모델

DB 스키마 변경 없음.
`Album.status`의 기존 `PROCESSING`, `ACTIVE`, `DELETED` 상태를 그대로 사용한다.

## 5. 처리 흐름

`AlbumVideoProcessingService.processVideosAsync(albumId, memberId, videoEntries)`

1. 영상별 업로드 결과를 index별 map에 저장한다.
2. 업로드 성공 URL은 별도 cleanup 대상 목록에도 추가한다.
3. 모든 영상 처리에 성공하면 앨범을 조회해 `completeVideoProcessing()`으로 `ACTIVE` 전환한다.
4. 성공 시 `AlbumCreatedEvent`를 발행한다.
5. 처리 중 예외가 발생하면 cleanup 대상 URL을 best-effort로 삭제한다.
6. 예외 발생 시 앨범을 조회해 `delete()`로 `DELETED` 상태로 전환한다.
7. finally에서 `VideoEntry.tempFile`을 삭제한다.

`AlbumFileService.uploadAlbumVideoWithThumbnail(file, memberId)`

1. 영상 압축/길이 추출/썸네일 생성을 수행한다.
2. 영상 S3 업로드에 성공하면 `videoUrl`을 cleanup 대상으로 보관한다.
3. 썸네일 S3 업로드에 성공하면 `thumbnailUrl`을 cleanup 대상으로 보관한다.
4. 이후 예외가 발생하면 cleanup 대상 URL을 best-effort로 삭제한다.
5. 성공하면 `VideoUploadResult`를 반환하고 cleanup은 수행하지 않는다.

트랜잭션 경계:

- `AlbumVideoProcessingService.processVideosAsync()`의 기존 `@Async @Transactional` 경계는 유지한다.
- S3 삭제는 best-effort 외부 부수효과이며 삭제 실패가 DB 상태 전환을 막지 않는다.

## 6. 예외 / 에러 처리

- 비동기 처리 실패 시 예외를 로그로 남기고 앨범을 `DELETED` 상태로 전환한다.
- 업로드 성공 URL 삭제 실패 시 warn 로그만 남기고 원래 실패 흐름을 유지한다.
- 임시 파일 삭제 실패 시 기존처럼 warn 로그만 남긴다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 영상 비동기 처리 성공 시 앨범은 `ACTIVE`로 전환되고 `AlbumCreatedEvent`가 발행된다.
- [x] 영상 비동기 처리 실패 시 앨범은 `DELETED`로 전환된다.
- [x] 실패 전 업로드 성공한 영상/썸네일 URL은 삭제 요청된다.
- [x] 단일 영상 처리 중 썸네일 업로드가 실패하면 이미 업로드된 영상 URL이 삭제 요청된다.
- [x] 삭제 실패는 원래 실패 처리를 덮어쓰지 않는다.
- [x] 임시 파일 삭제는 finally에서 수행된다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- DB 스키마 변경 없음.
- API 계약 변경 없음.
- S3 업로드 성공분에 대한 보상 삭제 호출이 추가된다.

## 9. 미결정 사항 (Open Questions)

없음.

## 10. 참고

- Issue #424
- ADR-0010
- LLD-0006
