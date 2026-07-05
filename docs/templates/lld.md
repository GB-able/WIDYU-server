# LLD-XXXX: <기능 이름>

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.
> LLD 하나 = PR 하나가 원칙. "하나의 PR에 넣기엔 diff가 너무 많다(파일 15개 이상)"면 LLD를 분리한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Draft / Review / Approved / Superseded |
| Issue | #N |
| 관련 ADR | ADR-XXXX (없으면 -) |
| 작성자 | |
| 작성일 | YYYY-MM-DD |

## 1. 목적 / 배경
> 왜 이 기능이 필요한가. 1~3문장. 클라이언트 요구사항 또는 서버 내부 필요.

<이 기능이 왜 필요한가, 무엇을 해결하는가>

## 2. 범위
> Out of scope가 중요하다. 빠진 게 아니라 나중에 하기로 결정한 것임을 명시. 리뷰어가 "왜 없는지" 묻지 않게 된다.

### In scope
- 변경 모듈: widyu-api / widyu-domain (해당 명시)
-

### Out of scope
-

## 3. 인터페이스 / API
> HTTP 메서드 + 경로 목록. 요청/응답 JSON 예시 (필드명, 타입, nullable 여부). ApiResponse 래퍼 포함해서 작성.

```http
POST /api/v1/...
GET  /api/v1/...
```

```json
{
  "isSuccess": true,
  "code": "...",
  "message": "...",
  "result": {}
}
```

## 4. 데이터 모델
> 새로 만들거나 바뀌는 테이블/컬럼만 작성. 엔티티 전체를 쓰지 않는다.
> WIDYU: 엔티티 → widyu-domain, DTO → widyu-api/dto 위치 명시.

<엔티티, DTO, 테이블/마이그레이션>

## 5. 처리 흐름
> 번호 매긴 순서로. 트랜잭션 경계, @EventListener 사용 여부, Facade 유무 명시.
> 예) "1. @CurrentMember 인증 → 2. 검증 → 3. 저장 → 4. 이벤트 발행"

<시퀀스, 핵심 로직, 트랜잭션 경계>

## 6. 예외 / 에러 처리
> 에러 코드와 발생 조건 목록. 새 에러 코드는 여기서 정의.

<에러 케이스와 응답 코드>

## 7. 인수조건 (Acceptance Criteria)
> **이 항목들이 테스트 시나리오가 된다.** Swagger 반영, 빌드 통과도 항목에 포함.
> 구현 완료 후 체크. Codex review가 이 목록 기준으로 검수.

- [ ] (기능 조건 1)
- [ ] (기능 조건 2)
- [ ] Swagger에 성공/주요 예외 응답이 반영된다.
- [ ] `./gradlew :backend:widyu-api:test`가 통과한다.

## 8. 영향 범위 / 마이그레이션
> 기존 코드가 바뀌는 부분. 새 테이블이면 "신규, 기존 영향 없음". MySQL ENUM 추가면 ALTER TABLE 명령.

<기존 코드·데이터·배포 영향>

## 9. 미결정 사항 (Open Questions)
> ⚠️ 작성 시점에 결정 안 된 항목. 구현 전에 채울 것. **추측으로 채우지 말 것.**
> 구현 완료 후 "없음"으로 업데이트. PR 본문 Open Questions와 동기화.

- [ ]

## 10. 참고
<링크, 레퍼런스>
