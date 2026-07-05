# Swagger API Spec Guide

## 목적

로직 구현 전에 API 계약을 먼저 확인할 수 있도록 Swagger 명세 작성 기준을 통일한다.

## 생성 방식

- Swagger 명세는 `controller/docs/` 패키지의 `*Docs` 인터페이스로 작성한다.
- 컨트롤러는 해당 인터페이스를 구현해 비즈니스 로직만 담는다 (annotation 분리).
- `springdoc-openapi-starter-webmvc-ui`가 런타임에 명세를 자동 생성한다.

## 공통 응답

모든 API는 아래 포맷을 사용한다.

```json
{
  "isSuccess": true,
  "code": "string",
  "message": "string",
  "result": {}
}
```

## 인증

- 인증이 필요한 API는 `Authorization: Bearer {token}` 헤더를 사용한다.
- `*Docs` 인터페이스에 `@SecurityRequirement(name = "bearerAuth")`를 붙인다.
- 개별 필드에 Authorization 헤더를 반복 문서화하지 않는다.

## 목록 조회와 커서 페이징

- 최초 조회는 `cursor` 생략.
- 다음 페이지가 있으면 응답의 `nextCursor`를 다음 요청 `cursor`로 전달.
- 마지막 페이지는 `nextCursor: null`, `hasNext: false`.

## 파일 업로드 흐름

현재 WIDYU는 서버가 `MultipartFile`을 받아 `S3Service.uploadFile()`로 S3에 업로드하고, 결과 URL을 도메인 데이터에 저장한다.
Swagger 명세에는 아래 항목을 명확히 적는다.

- 요청 `consumes`: `multipart/form-data`
- 파일 필드명과 최대 개수
- 이미지/영상 허용 확장자와 용량 제한
- 서버가 반환하는 URL 필드

Presigned URL + `imageKey` 방식은 현재 구현이 아니다. 이 방식으로 전환하려면 별도 ADR/LLD에서 API 계약, 미연결 오브젝트 정리 정책, 보안 조건을 먼저 결정한다.

## `*Docs` 인터페이스 작성 규칙

- 인터페이스 위치: `controller/docs/`
- 클래스명: `{Domain}ControllerDocs`
- 각 메서드에 `@Operation(summary = "...", description = "...")` 작성.
- description에는 화면 분기, 입력 책임, 페이징 기준, 이미지 업로드 선행 조건처럼 클라이언트 구현에 필요한 정책을 포함한다.
- 에러 응답은 `@ApiResponse` 어노테이션으로 명시한다.
- 미구현 API는 summary 앞에 `[미구현]` 표시.

## 도메인별 명세 기준

- Auth: provider token 로그인과 서버 callback 로그인 구분.
- Album: multipart 업로드 필드, 파일 제한, 공유 대상 범위 명시.
- Health: 건강 목표 유형별 필수/nullable 필드 명시.
- Location: WebSocket 엔드포인트는 Swagger에 포함되지 않으므로 별도 문서(apiDocs/)로 관리.
- Pay: 포인트 잔액, 결제 상태 전이 규칙 description에 포함.

## 제외 범위

Swagger 명세 작업에서는 DB 저장, S3 업로드, FCM 발송 같은 실제 비즈니스 로직을 구현하지 않는다.
