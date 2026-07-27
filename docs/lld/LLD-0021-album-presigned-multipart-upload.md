# LLD-0021: 앨범 Presigned Multipart 직접 업로드

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Approved |
| Issue | #450 |
| 관련 ADR | ADR-0015 (앨범 Presigned Multipart 직접 업로드), ADR-0004 |
| 작성자 | dongkyunKim |
| 작성일 | 2026-07-27 |

## 1. 목적 / 배경

앨범 미디어(최대 2GB 영상)가 클라이언트 → API 서버 → S3로 두 번 전송되는 서버 경유 구조를,
클라이언트가 S3에 직접 업로드하는 Presigned URL 방식으로 전환한다.
API 서버는 업로드 세션 발급과 완료 검증만 담당하고, 영상 처리(FFmpeg)는 기존 비동기 파이프라인을 재사용한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api (album, global/infrastructure/s3, global/config), widyu-domain (AlbumUploadSession, ErrorCode)
- 업로드 세션 발급 API — 이미지: presigned PUT, 영상: multipart 파트별 presigned URL
- 업로드 완료 API — `CompleteMultipartUpload`, `HeadObject` 검증, 앨범 저장, 멱등 처리
- 업로드 세션 Redis 저장 (`@RedisHash` + `@TimeToLive`)
- 영상 스테이징 원본을 임시 파일로 다운로드 후 기존 `AlbumVideoProcessingService` 파이프라인 재사용
- 완료 시 이미지 스테이징 → 최종 prefix 서버사이드 복사

### Out of scope
- 기존 서버 경유 API(`POST /api/v1/albums/upload`) 변경·폐기 — 유지한다
- S3 Event/Lambda/SQS 기반 완료 감지 (ADR-0015에서 기각)
- 업로드 진행률 조회 API, 세션 명시적 취소(abort) API
- S3 Lifecycle 규칙 적용 자체 (운영 버킷 설정 작업 — `## 8`에 명시)
- 다른 도메인(복약 인증 이미지 등) 업로드 경로 전환

## 3. 인터페이스 / API

```http
POST /api/v1/albums/uploads                       # 업로드 세션 발급
POST /api/v1/albums/uploads/{sessionId}/complete  # 업로드 완료
```

### 3-1. 세션 발급

Request (`application/json`):
```json
{
  "files": [
    { "fileName": "trip.mp4", "contentType": "video/mp4", "fileSize": 293601280 },
    { "fileName": "photo.jpg", "contentType": "image/jpeg", "fileSize": 2097152 }
  ]
}
```
- `files`: required, 1~8개
- `fileName`: required / `contentType`: required, AlbumMediaPolicy 허용 타입 / `fileSize`: required, 양수(바이트)

Response: HTTP 200
```json
{
  "isSuccess": true,
  "code": "ALBM_2012",
  "message": "업로드 세션이 발급되었습니다.",
  "data": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",
    "expiresInSeconds": 3600,
    "files": [
      {
        "index": 0,
        "mediaType": "VIDEO",
        "objectKey": "albums/staging/1/550e8400.../0_a1b2c3d4.mp4",
        "uploadUrl": null,
        "partSizeBytes": 10485760,
        "parts": [
          { "partNumber": 1, "uploadUrl": "https://..." },
          { "partNumber": 2, "uploadUrl": "https://..." }
        ]
      },
      {
        "index": 1,
        "mediaType": "PHOTO",
        "objectKey": "albums/staging/1/550e8400.../1_e5f6a7b8.jpg",
        "uploadUrl": "https://...",
        "partSizeBytes": null,
        "parts": null
      }
    ]
  }
}
```
- 이미지(`PHOTO`): `uploadUrl` 단건 presigned PUT — Content-Type·Content-Length가 서명에 포함되므로 클라이언트는 선언한 값 그대로 전송해야 한다
- 영상(`VIDEO`): `partSizeBytes`(10MB) 단위로 분할한 `parts` presigned URL 목록 — 마지막 파트만 나머지 크기, 각 파트 업로드 응답의 `ETag`를 수집한다

### 3-2. 업로드 완료

Request (`application/json`):
```json
{
  "content": "가족 여행 영상입니다",
  "files": [
    {
      "index": 0,
      "parts": [
        { "partNumber": 1, "eTag": "\"9b2cf535f27731c974343645a3985328\"" },
        { "partNumber": 2, "eTag": "\"d41d8cd98f00b204e9800998ecf8427e\"" }
      ]
    }
  ]
}
```
- `content`: optional, max 2200자
- `files`: 영상 파일 index별 파트 ETag 목록. 이미지는 항목 불필요
- 세션의 모든 영상 index가 포함되어야 한다

Response: HTTP 202 (기존 업로드 API와 동일 계약)
```json
{
  "isSuccess": true,
  "code": "ALBM_2013",
  "message": "앨범 업로드 완료 요청이 접수되었습니다.",
  "data": { "albumId": 1 }
}
```
- 동일 세션에 대한 중복 완료 요청은 동일한 `albumId`를 반환한다 (멱등)

## 4. 데이터 모델

### AlbumUploadSession (widyu-domain, Redis)

완료 처리 동시성 제어용 락 키: `albumUploadSession:completing:{sessionId}` (setIfAbsent, TTL 600s)

```java
@RedisHash("albumUploadSession")
public class AlbumUploadSession {
    @Id String id;                       // UUID
    Long memberId;
    AlbumUploadSessionStatus status;     // WAITING / COMPLETED
    Long albumId;                        // COMPLETED 후 멱등 응답용
    List<AlbumUploadSessionFile> files;
    @TimeToLive long ttl;                // WAITING: 21600s(6h), COMPLETED: 600s(10m)
}

AlbumUploadSessionFile {
    int index;
    MediaType mediaType;                 // PHOTO / VIDEO (기존 enum 재사용)
    String fileName;
    String contentType;
    long fileSize;
    String objectKey;                    // 서버 생성, albums/staging/{memberId}/{sessionId}/{index}_{uuid8}.{ext}
    String uploadId;                     // VIDEO만, CreateMultipartUpload 결과
    int partCount;                       // VIDEO만
}
```

- MySQL 스키마 변경 없음 (Album 엔티티 그대로)
- Repository: `AlbumUploadSessionRepository extends CrudRepository<AlbumUploadSession, String>` (widyu-api)

### DTO (widyu-api, album/dto)

- `request/AlbumUploadSessionCreateRequest(List<FileMetadata> files)`
- `request/AlbumUploadCompleteRequest(String content, List<CompletedFile> files)`
- `response/AlbumUploadSessionResponse` — `from()` 팩토리
- `response/AlbumUploadAcceptedResponse` — `from()` 팩토리 추가 (기존 record 재사용)

## 5. 처리 흐름

### 5-1. 세션 발급 (`AlbumUploadSessionFacadeImpl.createUploadSession`)

```
1. @CurrentMember 인증 → memberUtil.getCurrentMember()
2. AlbumMediaPolicy.validateMetadata(files)
   - 개수(전체 8, 사진 8, 영상 3), 허용 contentType, 크기(사진 10MB, 영상 2GB), fileSize > 0
3. sessionId(UUID) 생성, 파일별 objectKey 생성 (서버가 생성, 클라이언트 지정 불가)
4. 파일별 presign
   - PHOTO: S3DirectUploadService.presignPut(objectKey, contentType, fileSize, 1h)
   - VIDEO: createMultipartUpload(objectKey, contentType) → uploadId
            partCount = ceil(fileSize / 10MB), 파트별 presignUploadPart(objectKey, uploadId, partNumber, 1h)
5. AlbumUploadSession(WAITING, ttl=21600) Redis 저장
6. AlbumUploadSessionResponse 반환 (트랜잭션 없음 — DB 접근 없음)
```

### 5-2. 업로드 완료 (`AlbumUploadSessionFacadeImpl.completeUpload`)

```
1. @CurrentMember 인증
2. 세션 조회 — 없으면 ALBUM_UPLOAD_SESSION_NOT_FOUND (만료 포함)
3. 소유자 검증 — 불일치 시 ALBUM_UPLOAD_SESSION_FORBIDDEN
4. status == COMPLETED면 저장된 albumId로 즉시 응답 (멱등)
5. 완료 락 획득 — Redis setIfAbsent(albumUploadSession:completing:{id}, TTL 600s)
   - 실패 시 세션 재조회: COMPLETED면 albumId로 멱등 응답, 아니면 ALBUM_UPLOAD_ALREADY_IN_PROGRESS(409)
   - 동시 완료 요청이 각각 앨범을 생성하거나 늦은 요청의 실패 정리가 앞선 요청의 산출물을 삭제하는 것을 차단
6. 영상 파트 검증 — 세션의 모든 VIDEO index에 partCount만큼 파트가 있어야 함, 없으면 ALBUM_UPLOAD_INCOMPLETE
7. VIDEO별 CompleteMultipartUpload(objectKey, uploadId, parts)
   - S3 오류(파트 누락·ETag 불일치) → ALBUM_UPLOAD_INCOMPLETE
8. 파일별 HeadObject 검증
   - 객체 없음 / contentLength != 선언 fileSize / contentType != 선언값 → 스테이징 정리 후 ALBUM_UPLOAD_FILE_MISMATCH
9. PHOTO: 스테이징 → albums/photos/{memberId}/ 서버사이드 복사(CopyObject) 후 스테이징 삭제, mediaUrls에 최종 URL
   VIDEO: mediaUrls placeholder("")
10. AlbumService.saveAlbum(member, content, mediaUrls, thumbnailUrls, durations, hasVideos)
    - 기존 로직 재사용: 영상 없으면 ACTIVE + AlbumCreatedEvent, 있으면 PROCESSING
11. 세션 COMPLETED + albumId 저장 (ttl=600)
12. hasVideos면 AlbumVideoProcessingService.processStagedVideosAsync(albumId, memberId, stagedEntries)
13. HTTP 202 + albumId
```

실패 처리:
- 6~10단계 예외: 스테이징 객체 삭제·미완료 multipart abort·복사된 사진 삭제 후 락 해제, 예외 전파. 세션은 WAITING 유지(원본이 삭제되므로 사실상 재발급 필요 — TTL로 자동 소멸).
- 11단계(세션 완료 기록) 예외: 완료 기록 없이 202를 반환하면 락 만료 후 중복 앨범이 생길 수 있으므로, **앨범을 보상 삭제(DELETED)하고 산출물 정리 후 실패로 응답**한다. 재시도는 multipart가 이미 완료된 상태라 INCOMPLETE로 거부된다(중복 생성 없음).
- 12단계(비동기 처리 제출) 예외: 앨범이 영구 PROCESSING으로 남지 않도록 **앨범 보상 삭제 + 산출물 정리 + 세션 삭제 후 실패로 응답**한다(세션을 남기면 멱등 응답이 삭제된 앨범을 가리키게 됨).

### 5-3. 스테이징 영상 비동기 처리 (`AlbumVideoProcessingService.processStagedVideosAsync`)

```
@Async @Transactional
1. StagedVideoEntry(index, objectKey, originalFileName, contentType)별
   S3DirectUploadService.downloadToTempFile(objectKey) → File
2. VideoEntry로 변환 후 기존 처리 코어 재사용 (processVideos 내부 메서드로 추출)
   - uploadAlbumVideoWithThumbnail: 압축 → 길이 추출 → 썸네일 → 최종 prefix 업로드
   - album.completeVideoProcessing(...) → ACTIVE
   - AlbumCreatedEvent 발행
3. 실패 시: 업로드된 최종 파일 정리 + 앨범 DELETED (기존 보상 정책 ADR-0010 재사용)
4. finally: 임시 파일 삭제 + 스테이징 원본 객체 삭제
```

기존 `processVideosAsync`는 공통 코어(`processVideos`)를 호출하도록 리팩토링하되 동작은 동일하게 유지한다.

### 5-4. S3 인프라 (`global/infrastructure/s3`)

`S3DirectUploadService` 인터페이스 + `S3DirectUploadServiceImpl`:
- `presignPut(key, contentType, contentLength, duration)` — S3Presigner, Content-Type·Content-Length 서명 포함
- `createMultipartUpload(key, contentType)` / `presignUploadPart(key, uploadId, partNumber, duration)`
- `completeMultipartUpload(key, uploadId, parts)` / `abortMultipartUpload(key, uploadId)`
- `headObject(key)` → (contentLength, contentType) / `copyObject(sourceKey, destKey)` / `deleteObject(key)`
- `downloadToTempFile(key, suffix)` — GetObject를 임시 파일로 저장

`S3Config`에 `S3Presigner` 빈 추가 (기존 `S3Client`와 동일 자격증명·리전).

## 6. 예외 / 에러 처리

| 상황 | 에러 코드 | HTTP |
|------|-----------|------|
| files 비어있음 / fileSize ≤ 0 | FILE_IS_EMPTY (기존) | 400 |
| 허용하지 않는 contentType | INVALID_FILE_TYPE (기존) | 400 |
| 개수 초과 (전체 8 / 사진 8 / 영상 3) | BAD_REQUEST + 메시지 (기존) | 400 |
| 사진 10MB / 영상 2GB 초과 | FILE_TOO_LARGE (기존) | 400 |
| 세션 없음·만료 | **ALBUM_UPLOAD_SESSION_NOT_FOUND** `ALBUM_UPLOAD_4040` | 404 |
| 세션 소유자 불일치 | **ALBUM_UPLOAD_SESSION_FORBIDDEN** `ALBUM_UPLOAD_4030` | 403 |
| 영상 파트 누락·ETag 불일치·complete 실패 | **ALBUM_UPLOAD_INCOMPLETE** `ALBUM_UPLOAD_4001` | 400 |
| HEAD 검증 실패 (객체 없음·크기·타입 불일치) | **ALBUM_UPLOAD_FILE_MISMATCH** `ALBUM_UPLOAD_4002` | 400 |
| 동일 세션 완료 처리가 이미 진행 중 | **ALBUM_UPLOAD_ALREADY_IN_PROGRESS** `ALBUM_UPLOAD_4090` | 409 |
| presign·S3 조작 실패 (그 외) | FILE_UPLOAD_FAILED (기존) | 500 |

굵은 항목은 `ErrorCode`(widyu-domain)에 신규 추가한다.
비동기 처리 실패는 HTTP로 반환하지 않고 기존 정책대로 앨범 DELETED + 스테이징·업로드 산출물 정리로 노출을 막는다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 세션 발급 API가 메타데이터 검증 후 이미지에는 presigned PUT, 영상에는 파트별 presigned URL을 반환한다
- [x] object key는 서버가 `albums/staging/{memberId}/{sessionId}/` 아래로 생성한다
- [x] 정책 위반 메타데이터(개수·타입·크기)는 세션 발급 단계에서 거부된다
- [x] 완료 API가 영상 multipart를 완료하고 HEAD로 크기·Content-Type을 선언값과 대조한다
- [x] HEAD 검증 실패 시 스테이징 객체가 정리되고 ALBUM_UPLOAD_FILE_MISMATCH를 반환한다
- [x] 완료 API 중복 호출 시 동일 albumId로 멱등 응답한다
- [x] 다른 회원의 세션으로 완료 요청 시 403을 반환한다
- [x] 영상 포함 앨범은 PROCESSING으로 저장되고 비동기 처리 후 ACTIVE로 전환된다
- [x] 이미지 전용 앨범은 즉시 ACTIVE로 저장되고 AlbumCreatedEvent가 발행된다
- [x] 비동기 처리 실패 시 앨범이 DELETED 처리되고 임시 파일·스테이징 원본이 정리된다
- [x] 비동기 처리 성공 시 스테이징 원본이 삭제된다
- [x] 기존 `POST /api/v1/albums/upload`는 변경 없이 동작한다
- [x] Swagger에 성공/주요 예외 응답이 반영된다
- [x] `./gradlew :backend:widyu-api:test`가 통과한다

## 8. 영향 범위 / 마이그레이션

- DB 스키마 변경 없음 (Redis 신규 hash `albumUploadSession`만 추가)
- 기존 업로드 API·영상 처리 파이프라인 동작 변경 없음 (`processVideosAsync` 내부 코어 추출 리팩토링만)
- **운영 버킷 설정 작업 (배포 전 필요)**:
  - S3 Lifecycle 규칙 1: `AbortIncompleteMultipartUpload` — 시작 1일 후 미완료 multipart 자동 중단
  - S3 Lifecycle 규칙 2: prefix `albums/staging/` — 7일 후 객체 만료
  - CORS 설정: 앱/웹 클라이언트의 S3 직접 PUT 허용 (`PUT`, `ETag` 헤더 노출)
- 성능 참고: 초기 응답은 URL 발급만 수행하므로 파일 크기와 무관하게 짧아진다. 전체 영상 처리 시간은 동일(서버가 S3에서 재다운로드).

## 9. 미결정 사항 (Open Questions)

없음.

- presigned URL 만료 1시간, 세션 TTL 6시간, 완료 세션 TTL 10분, 파트 크기 10MB는 서비스 상수로 시작하고 운영 피드백에 따라 설정으로 승격한다.
- 기존 서버 경유 API 폐기 시점은 클라이언트 전환 완료 후 별도 결정한다 (ADR-0015 후속).

## 10. 참고

- ADR-0015 (본 설계 결정), ADR-0004 (기존 서버 경유 전략), ADR-0010 (실패 보상 삭제), ADR-0011 (알림 격리)
- LLD-0006 (기존 비동기 파이프라인 — 재사용 대상)
- AWS S3 Multipart Upload: https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html
- 미완료 multipart Lifecycle 정리: https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpu-abort-incomplete-mpu-lifecycle-config.html
