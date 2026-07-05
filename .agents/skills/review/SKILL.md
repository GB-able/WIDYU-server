---
name: review
description: Claude Code가 작성한 코드를 LLD 기준으로 검수한다. 사용자가 "검수해줘", "리뷰해줘", "Claude 결과물 확인해줘" 등을 요청할 때 사용.
---

# review

Claude Code가 작성한 코드 변경을 LLD + 코딩 규칙 기준으로 검수한다.
문제가 있으면 구체적인 파일·라인과 함께 보고한다. 없으면 PR 진행을 승인한다.

## 절차

1. `git diff HEAD~1..HEAD` 또는 `git diff develop...HEAD`로 변경 diff를 읽는다.
2. 변경 파일에서 관련 LLD를 추론한다. `docs/lld/`에서 해당 LLD를 읽는다.
3. `bash scripts/harness/validate-java-rules.sh <변경된 .java 파일>` 을 실행한다.
4. 아래 검수 체크리스트를 하나씩 확인한다.
5. 결과를 보고한다.

## 검수 체크리스트

### 1. LLD 인수조건 충족
- LLD `## 7. 인수조건` 항목을 하나씩 읽고 코드에서 대응하는 구현이 있는지 확인한다.
- 미구현 항목이 있으면 목록으로 보고한다.

### 2. 코딩 규칙 (validate-java-rules.sh 결과 포함)
- [ ] 삼항 연산자(`? :`) 없음
- [ ] Service/Facade에서 `new XxxResponse(` 직접 생성 없음 — `from()`/`of()` 사용 여부
- [ ] Controller에서 Repository 직접 import 없음
- [ ] `widyu-api`에 `@Entity` 없음
- [ ] `widyu-domain`에 Repository 없음
- [ ] `@Async` 메서드에 `@Transactional` 없는 경우 경고

### 3. 모듈 배치
- [ ] 신규 엔티티는 `widyu-domain`에 위치
- [ ] 신규 Repository/Service/Controller는 `widyu-api`에 위치

### 4. 테스트
- [ ] LLD 인수조건 항목에 대응하는 테스트 메서드가 존재하는가
- [ ] 테스트가 `@ExtendWith(MockitoExtension.class)` + BDDMockito 패턴인가
- [ ] 테스트 메서드명이 한글 언더스코어 형식인가

### 5. 엔티티 변경 (해당 시)
- [ ] `docs/erd/`의 ERD 문서가 변경 사항을 반영하고 있는가
- [ ] MySQL ENUM 변경이 있으면 PR 본문 비고에 ALTER TABLE 명시 여부

### 6. Swagger (해당 시)
- [ ] 신규/변경 API에 `controller/docs/`의 `*Docs` 인터페이스가 업데이트됐는가

## 보고 형식

```
## Codex Review 결과

### ✅ 통과 항목
- (항목 목록)

### ❌ 문제 항목
- [파일경로:라인] 문제 설명
  → 수정 방향

### ⚠️ 경고 (PR 블로커 아님)
- (항목 목록)

### 판정
APPROVE / REQUEST_CHANGES
```

## 하지 말 것

- 코드를 직접 수정하기 (review는 보고만 한다, 수정은 implement 또는 Claude가 담당).
- LLD가 없는 경우 코딩 규칙과 모듈 배치만 확인하고 LLD 항목은 건너뛴다.
