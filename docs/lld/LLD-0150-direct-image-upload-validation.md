# LLD-0150: 복약 인증 사진과 프로필 사진 업로드의 형식, 크기 검사

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #1053 |
| 관련 ADR | - |
| 작성자 | Claude |
| 작성일 | 2026-11-14 |

## 1. 목적 / 배경

복약 인증 사진(`POST /api/v1/goals/medicine-schedules/{scheduleId}/verify`)과 프로필 사진(보호자 본인, 시니어 본인, 방장이 바꾸는 시니어 프로필) 업로드는 아무 검사 없이 S3에 올리고 공개 URL을 저장한다. 저장 객체의 `Content-Type`과 키의 확장자를 클라이언트가 보낸 값 그대로 쓴다. 그래서 인증된 회원이 `text/html`이나 스크립트가 든 SVG를 올리면 그 URL을 연 사람의 브라우저에서 S3(또는 CDN) 오리진으로 스크립트가 실행되고, 크기 상한도 전역 multipart 1000MB와 nginx 100MB뿐이다.

앨범 사진은 `AlbumMediaPolicy`의 허용 목록과 10MB 상한을 이미 쓴다. 두 경로에도 같은 기준을 적용하고, 저장 형식은 파일 내용으로 정한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api
- `AlbumMediaPolicy.validateImageFile(MultipartFile)`을 추가한다. 앨범 사진 허용 목록(`ALLOWED_IMAGES`)과 사진 상한(`MAX_PHOTO_BYTES`, 10MB)을 그대로 쓰고, SVG만 뺀다.
- 형식은 파일 앞 12바이트의 시그니처(매직 바이트)로 정한다. 클라이언트의 `Content-Type`과 파일명은 쓰지 않는다.
- 저장 키의 확장자와 S3 객체의 `Content-Type`을 판별한 형식에서 정한다. `S3Service.uploadFile(file, filePath, contentType)`을 추가한다.
- 복약 인증은 여러 장 중 한 장이라도 어긋나면 한 장도 올리지 않는다.
- Swagger에 400 사유를 적는다.

### Out of scope
- 앨범 허용 목록에서 SVG를 빼는 일. 앨범 동작이 바뀌므로 따로 정한다(Open Questions 2).
- S3 객체 메타데이터 `Content-Disposition`, `X-Content-Type-Options: nosniff`. 버킷, CDN 설정과 함께 정해야 해서 후속으로 둔다(Open Questions 3).
- 이미지 디코딩, 재인코딩, EXIF 제거. 새 라이브러리가 필요하다.
- 앨범 업로드(멀티파트, 업로드 세션)의 시그니처 검사. 앨범은 지금처럼 클라이언트 `Content-Type`으로 검사한다.

## 3. 인터페이스 / API

엔드포인트와 요청, 응답 형식은 바뀌지 않는다. 받지 않는 파일에 400이 새로 생긴다.

```http
POST  /api/v1/goals/medicine-schedules/{scheduleId}/verify   (medicationProofImage, 0장 이상)
PATCH /api/v1/mypage/guardian/profile/image                 (image)
PATCH /api/v1/mypage/guardian/seniors/{memberId}/image      (image, 방장만)
PATCH /api/v1/mypage/senior/profile/image                   (image)
```

```json
{
  "isSuccess": false,
  "code": "FILE_4002",
  "message": "지원하지 않는 파일 형식입니다.",
  "result": null
}
```

## 4. 데이터 모델

변경 없음. 판별 결과는 `AlbumMediaPolicy.ImageFormat(contentType, extension)` 레코드로 돌려준다.

| 시그니처 | 저장 Content-Type | 확장자 |
| --- | --- | --- |
| `FF D8 FF` | `image/jpeg` | `jpg` |
| `89 50 4E 47 0D 0A 1A 0A` | `image/png` | `png` |
| `GIF87a`, `GIF89a` | `image/gif` | `gif` |
| `RIFF` + 8번째 바이트부터 `WEBP` | `image/webp` | `webp` |
| `BM` | `image/bmp` | `bmp` |
| 4번째 바이트부터 `ftyp` + 브랜드 `heic`, `heix`, `hevc`, `hevx`, `heim`, `heis` | `image/heic` | `heic` |
| 4번째 바이트부터 `ftyp` + 브랜드 `mif1`, `msf1`, `heif` | `image/heif` | `heif` |

판별한 형식이 `ALLOWED_IMAGES`에 없으면 거부한다. 앨범 허용 목록에서 형식을 빼면 이 경로에서도 함께 빠진다. SVG는 텍스트라 시그니처로 판별되지 않고, 판별 결과가 SVG여도 거부한다.

## 5. 처리 흐름

복약 인증(`MedicationProofService.verifyMedication`, 트랜잭션 밖):
1. 현재 회원 조회 → `validateBeforeUpload`(시간 범위, 중복) 사전 검증
2. 모든 사진을 `validateImageFile`로 검사. 하나라도 실패하면 예외, S3 호출 없음
3. 사진마다 `generateFilePath("medication-proof/{memberId}", "proof." + 확장자)` → `uploadFile(file, path, 판별 Content-Type)`
4. 인증 트랜잭션. 실패하면 올린 사진 보상 삭제(기존 동작)

프로필(`MyPageProfileService.updateMemberProfileImage`, 호출자 트랜잭션 안):
1. (시니어 프로필은 방장 확인 뒤) `validateImageFile`로 검사
2. `generateFilePath("profile", "profile." + 확장자)` → `uploadFile(file, path, 판별 Content-Type)`
3. 회원 프로필 URL 교체 → 기존 사진 삭제(기존 동작)

## 6. 예외 / 에러 처리

| 조건 | 에러 코드 |
| --- | --- |
| 파일이 없거나 0바이트 | `FILE_IS_EMPTY` (400, FILE_4000) |
| 시그니처가 표의 형식이 아니거나 SVG, 또는 `ALLOWED_IMAGES`에 없는 형식 | `INVALID_FILE_TYPE` (400, FILE_4002) |
| 10MB 초과 | `FILE_TOO_LARGE` (400, FILE_4004, "사진은 최대 10MB까지 업로드 가능합니다.") |
| 업로드 스트림을 읽지 못함 | `FILE_UPLOAD_FAILED` (500, FILE_5000) |

새 에러 코드는 없다.

## 7. 인수조건 (Acceptance Criteria)

- [x] 이미지 `Content-Type`을 붙인 HTML, SVG, 일반 텍스트는 `INVALID_FILE_TYPE`으로 거부되고 S3에 올라가지 않는다.
- [x] 10MB를 넘는 사진은 `FILE_TOO_LARGE`로 거부된다.
- [x] JPEG, PNG, GIF, WEBP, BMP, HEIC, HEIF는 클라이언트 `Content-Type`이 `application/octet-stream`이어도 통과하고, 저장 Content-Type과 확장자는 시그니처에서 정해진다.
- [x] 복약 인증에서 한 장이라도 거부되면 다른 사진도 올리지 않는다.
- [x] 정상 PNG 프로필 사진은 기존처럼 올라가고 기존 사진을 지운다.
- [x] Swagger에 400 사유가 반영된다.
- [x] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션

- 앱 확인(읽기 전용, 2026-11-14): 위듀 앱 복약 카메라는 `camera` 플러그인 `takePicture()`로 JPEG를 만들고, 위듀, 위듀케어 앱의 프로필은 `image_picker`의 `imageQuality: 80`으로 고른다. 두 앱 모두 Dio `MultipartFile.fromFile`로 `Content-Type`을 직접 지정하지 않는다. 서버가 클라이언트 `Content-Type`을 보지 않으므로 이 값이 무엇이든 정상 사진은 통과한다.
- 저장 키 확장자가 원래 파일명 대신 판별 형식으로 바뀐다(예: `photo.JPG` → `..._xxxx.jpg`). 이미 저장한 URL은 그대로다.
- DB, 설정 변경 없음.

## 9. 미결정 사항 (Open Questions)

1. **허용 형식과 크기 상한 (기본값으로 정함, 정책 확인 필요)**: 앨범 사진과 같은 형식(SVG 제외), 같은 10MB로 정했다. 프로필 사진에 더 작은 상한이 필요한지 확인이 필요하다.
2. **앨범 허용 목록의 SVG (확인 필요)**: 앨범은 여전히 `image/svg+xml`을 받는다. 같은 위험이 있어 빼는 것을 권하지만 앨범 동작 변경이라 이번에 하지 않았다.
3. **S3 객체 `Content-Disposition`, `nosniff` (후속)**: 버킷, CDN 응답 헤더 설정과 함께 정한다.
4. **`BM` 시그니처의 약함**: BMP는 앞 2바이트만 본다. `BM`으로 시작하는 텍스트도 `image/bmp`로 저장되지만 브라우저는 이를 이미지로만 다루므로 스크립트가 실행되지 않는다.

## 10. 참고

- 2026-11-14 저장소 점검(정적 리뷰, 기준 커밋 `497365c4`)
- `AlbumMediaPolicy`의 HEIC, HEIF 허용(#1041)
