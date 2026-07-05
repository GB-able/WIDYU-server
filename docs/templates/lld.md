# LLD-XXXX: <기능 이름>

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Draft / Review / Approved / Superseded |
| Issue | #N |
| 관련 ADR | ADR-XXXX (없으면 -) |
| 작성자 | |
| 작성일 | YYYY-MM-DD |

## 1. 목적 / 배경
<이 기능이 왜 필요한가, 무엇을 해결하는가>

## 2. 범위
### In scope
- 변경 모듈: widyu-api / widyu-domain (해당 명시)
-

### Out of scope
-

## 3. 인터페이스 / API
<엔드포인트, 요청/응답 스펙, 주요 함수 시그니처>

## 4. 데이터 모델
<엔티티, DTO, 테이블/마이그레이션>
> 엔티티 → widyu-domain, DTO → widyu-api/dto

## 5. 처리 흐름
<시퀀스, 핵심 로직, 트랜잭션 경계>
> Facade 사용 여부, @EventListener 사용 여부 명시

## 6. 예외 / 에러 처리
<에러 케이스와 응답 코드>

## 7. 인수조건 (Acceptance Criteria)
> 이 항목들이 테스트 시나리오가 된다.
- [ ]
- [ ]

## 8. 영향 범위 / 마이그레이션
<기존 코드·데이터·배포 영향>
> MySQL ENUM 변경 시 ALTER TABLE 명령 명시

## 9. 미결정 사항 (Open Questions)
> ⚠️ 결정되지 않은 항목. PR 본문에서 빈칸으로 처리되며 확인 대상이 된다. 추측으로 채우지 말 것.
- [ ]

## 10. 참고
<링크, 레퍼런스>
