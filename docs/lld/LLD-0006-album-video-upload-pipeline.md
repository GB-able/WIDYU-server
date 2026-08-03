# LLD-0006: 앨범 영상 업로드 비동기 처리 파이프라인

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | - |
| 관련 ADR | ADR-0004 (미디어 업로드 전략) |
| 작성자 | dongkyunKim |
| 작성일 | 2026-07-05 |

## 1. 목적 / 배경

영상 압축·썸네일 생성·S3 업로드를 HTTP 요청 안에서 끝까지 처리하면 사용자는 업로드 완료 전까지 앱 흐름이 막힌다.
앨범 업로드는 먼저 요청을 접수하고, 영상 처리는 별도 async 트랜잭션에서 진행한다.
또한 압축 완료 파일을 썸네일 생성과 길이 추출에 직접 전달해 불필요한 디스크 복사를 제거한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api (album, global/infrastructure/video), widyu-domain (Album 상태)
- 앨범 업로드 요청 202 Accepted 응답
- 이미지 즉시 S3 업로드
- 영상 임시 파일 저장 후 async 처리
- 영상 압축, 길이 추출, 썸네일 생성
- 영상/썸네일 S3 업로드
- `Album.PROCESSING → ACTIVE` 상태 전환
- 실패 시 처리 중 앨범 삭제 처리
- 임시 파일 정리

### Out of scope
- Presigned URL 직접 업로드
- 메시지 큐 기반 재시도
- 서버 재시작 시 in-flight async 작업 복구
- 프론트 업로드 진행률 표시

## 3. 인터페이스 / API

```http
POST /api/v1/albums/upload
Content-Type: multipart/form-data
```

Request:
- `content`: optional, max 2200자
- `mediaFiles`: required, 1~8개

Response: HTTP 202
```json
{
  "isSuccess": true,
  "code": "ALBM_2001",
  "message": "앨범 업로드 요청이 접수되었습니다.",
  "data": {
    "albumId": 1
  }
}
```

## 4. 데이터 모델

### Album (widyu-domain)

영상이 포함된 업로드는 먼저 `PROCESSING` 상태로 저장한다.

```
album
├── album_id (PK)
├── member_id
├── content
├── status = PROCESSING / ACTIVE / DELETED
├── media_urls       ← 영상 위치는 빈 문자열 placeholder 후 async 완료 시 교체
├── thumbnail_urls   ← async 완료 시 영상 썸네일 URL 반영
└── durations        ← async 완료 시 영상 길이 반영
```

`Album.completeVideoProcessing(videoUrlsByIndex, thumbnailUrlsByIndex, durationsByIndex)`:
- index별 placeholder를 실제 URL/길이로 교체한다. `@ElementCollection`은 null placeholder를 저장하지 않으므로, 재조회된 컬렉션이 비어 있으면 대상 index까지 확장한 뒤 교체한다.
- `status = ACTIVE`로 전환

### Async 작업 DTO

`AlbumVideoProcessingService.VideoEntry`:
```java
record VideoEntry(int index, File tempFile, String originalFileName, String contentType) {}
```

## 5. 처리 흐름

### 5-1. 업로드 요청 접수 (`AlbumFacadeImpl.uploadAlbum`)

```
1. currentMember 조회
2. AlbumFileService.prepareForAsyncUpload(mediaFiles, memberId)
   - AlbumMediaPolicy.validate()
   - image/*: 즉시 S3 업로드 후 mediaUrls에 URL 저장
   - video/*: MultipartFile을 temp File로 변환 후 VideoEntry 생성
   - 영상 위치에는 mediaUrls="", thumbnailUrls=null, durations=null placeholder 저장
3. AlbumService.saveAlbum(..., hasVideos)
   - 영상 없음: ACTIVE 앨범 저장 후 AlbumCreatedEvent 발행
   - 영상 있음: PROCESSING 앨범 저장, 이벤트 발행 보류
4. 영상이 있으면 AlbumVideoProcessingService.processVideosAsync(albumId, memberId, videoEntries)
5. HTTP 202 + albumId 반환
```

### 5-2. 영상 async 처리 (`AlbumVideoProcessingService.processVideosAsync`)

```
@Async
@Transactional
1. VideoEntry별 temp File을 MultipartFile wrapper로 변환
2. AlbumFileService.uploadAlbumVideoWithThumbnail(videoFile, memberId)
3. 결과를 index별 Map에 저장
   - videoUrls[index]
   - thumbnailUrls[index]
   - durations[index]
4. Album 조회
5. album.completeVideoProcessing(...)
6. AlbumCreatedEvent 발행
7. finally: 모든 VideoEntry.tempFile 삭제
```

실패 시:
```
catch Exception:
  log.error
  albumRepository.findById(albumId).ifPresent(Album::delete)
finally:
  tempFile 삭제
```

### 5-3. 영상 처리 세부 (`AlbumFileService.uploadAlbumVideoWithThumbnail`)

```
1. AlbumMediaPolicy.validate(file)
2. videoCompressionService.needsCompression(file)
   - true: compressVideo(MultipartFile) → tempCompressedFile
   - false: toTempFile(file) → sourceFile
3. extractDuration(sourceFile)
   - File을 직접 FFprobe로 분석 (추가 복사 없음)
4. generateThumbnail(sourceFile, duration)
   - seekSeconds = max(1.0, duration * 0.1)
5. S3 업로드
   - albums/videos/{memberId}/...
   - albums/thumbnails/{memberId}/...
6. VideoUploadResult(videoUrl, thumbnailUrl, duration) 반환
7. finally: 압축 파일, 썸네일 파일, 소유한 sourceFile 삭제
```

### 5-4. FFmpeg 처리 (`FFmpegVideoCompressionService`)

```
compressVideo:
  1. MultipartFile → temp input File
  2. FFprobe로 비디오 스트림 검증
  3. 500MB 이하가 될 때까지 최대 5회 압축
  4. 3분 초과 영상은 -t 180 적용

extractDuration:
  1. File path를 FFprobe로 직접 분석
  2. 첫 번째 VIDEO stream duration을 round

generateThumbnail:
  1. File path를 FFmpeg input으로 사용
  2. 영상 길이의 10% 지점, 최소 1초에서 1 frame 추출
  3. 640x480 jpg 생성
```

## 6. 예외 / 에러 처리

| 상황 | 처리 |
|------|------|
| mediaFiles null/empty/8개 초과 | AlbumMediaPolicy / Bean Validation 실패 |
| 지원하지 않는 contentType | INVALID_FILE_TYPE |
| S3 업로드 실패 | FILE_UPLOAD_FAILED |
| 압축/썸네일/길이 추출 실패 | FILE_UPLOAD_FAILED |
| async 처리 중 예외 | 로그 기록 후 앨범 `DELETED` 처리 |
| async 처리 완료 대상 앨범 없음 | ALBUM_NOT_FOUND |
| 임시 파일 삭제 실패 | warn 로그 후 계속 |

HTTP 202 이후 async 실패는 클라이언트 요청에 직접 예외로 반환할 수 없다.
현재 구현은 실패한 앨범을 삭제 상태로 바꾸는 방식으로 노출을 막는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 앨범 업로드 API는 영상 처리 완료를 기다리지 않고 HTTP 202와 albumId를 반환한다
- [x] 영상이 없는 앨범은 즉시 ACTIVE로 저장되고 AlbumCreatedEvent가 발행된다
- [x] 영상이 있는 앨범은 PROCESSING으로 저장되고 async 완료 후 ACTIVE로 전환된다
- [x] async 완료 전까지 영상 URL/썸네일/길이는 placeholder로 저장된다
- [x] 압축 완료 File은 길이 추출과 썸네일 생성에 직접 전달되어 추가 복사를 만들지 않는다
- [x] 썸네일은 영상 길이의 10% 지점, 최소 1초에서 추출된다
- [x] async 실패 시 처리 중 앨범은 삭제 상태가 된다
- [x] async 처리 후 임시 파일은 finally에서 삭제된다
- [x] Swagger에 202 Accepted 응답이 반영된다

## 8. 영향 범위 / 마이그레이션

- DB 스키마 변경 없음
- `Status.PROCESSING` 상태를 앨범 처리 중 상태로 사용한다
- 서버 재시작 시 실행 중인 async 작업은 복구되지 않는다
- 성능 기준:
  - 초기 응답 시간 `9.25s → 4.31s` (53% 단축)
  - 279MB 영상 기준 불필요한 디스크 쓰기 558MB 제거
  - 영상 길이 추출 `396ms → 113ms` (71% 단축)

## 9. 미결정 사항 (Open Questions)

없음. (백필 문서, 구현 완료 상태)

후속 개선 후보: 업로드 작업을 큐/워커 구조로 분리해 서버 재시작 시 in-flight 작업 유실을 줄이고, 실패 앨범의 사용자 안내 정책을 별도 정의한다.

## 10. 참고

- `AlbumController.java`: `/api/v1/albums/upload` 202 응답
- `AlbumFacadeImpl.java`: 접수 후 async 처리 분기
- `AlbumFileService.java`: 이미지 즉시 업로드, 영상 준비/처리
- `AlbumVideoProcessingService.java`: `@Async @Transactional` 영상 처리
- `FFmpegVideoCompressionService.java`: 압축, 길이 추출, 썸네일 생성
- `Album.java` (widyu-domain): PROCESSING/ACTIVE 상태 전환
- `apiDocs/api/album/video-benchmark-results.md`: 영상 처리 벤치마크
