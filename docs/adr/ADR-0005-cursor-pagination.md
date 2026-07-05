# ADR-0005: 커서 기반 페이징 전략

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-05 |
| 관련 | ERD-0001 |

## 맥락 (Context)

앨범 피드, 알림 목록, 캘린더 조회 등 대부분의 목록 API에서 페이징이 필요하다.
WIDYU는 시니어·보호자가 실시간으로 사진·영상을 올리는 서비스이므로, 데이터가 계속 추가되는 환경에서 일관된 결과를 보장해야 한다.

## 결정 (Decision)

**커서 기반 페이징**을 기본 전략으로 채택한다. 오프셋 페이징은 사용하지 않는다.

**커서 설계:**

| 도메인 | 커서 타입 | 이유 |
|--------|-----------|------|
| 앨범 피드 | `(createdAt, id)` 복합 커서 | 같은 시간대 앨범이 있을 수 있어 id로 타이브레이킹 |
| 알림 목록 | `id` 단일 커서 | id가 시간 순서와 동일, 단순성 우선 |
| 캘린더 | `id` 단일 커서 | 날짜 필터가 별도이므로 id 커서로 충분 |

**응답 표준:**
```json
{
  "data": {
    "items": [...],
    "hasNext": true,
    "nextCursor": 42
  }
}
```

- 최초 조회: `cursor` 파라미터 생략
- 다음 페이지: 이전 응답의 `nextCursor`를 `cursor`로 전달
- 마지막 페이지: `hasNext: false`, `nextCursor: null`

**쿼리 패턴:**
```sql
WHERE (:cursor IS NULL OR id < :cursor)
ORDER BY id DESC
LIMIT :size + 1  -- size+1개 조회로 hasNext 판단
```

복합 커서(앨범 피드):
```sql
WHERE (:cursorCreatedAt IS NULL OR (created_at < :cursorCreatedAt)
       OR (created_at = :cursorCreatedAt AND id < :cursorId))
ORDER BY created_at DESC, id DESC
```

**앨범 피드 인덱스 기준:**

앨범 피드는 `status = ACTIVE` 조건과 `created_at DESC, album_id DESC` 정렬을 동시에 만족해야 한다.
따라서 `Album` 엔티티에는 다음 복합 인덱스를 둔다.

```java
@Index(name = "idx_album_status_created_id",
       columnList = "status, created_at DESC, album_id DESC")
```

`SELECT a.id`로 먼저 커서 페이지의 ID만 조회한 뒤, 컬렉션 fetch는 `id IN :albumIds`로 분리한다.
이 방식은 정렬 대상 row 전체를 fetch join하지 않고, 인덱스 순서로 필요한 ID만 먼저 잘라내기 위한 선택이다.

## 고려한 대안 (Considered Options)

1. **오프셋 페이징 (`LIMIT n OFFSET m`)** — 구현 단순
   - 단점: 실시간 데이터 추가 시 중복/누락 발생 ("1페이지를 보는 사이 새 항목 추가 → 2페이지 첫 항목이 1페이지 마지막과 중복")
   - 데이터 증가에 따라 OFFSET 성능 선형 저하

2. **커서 기반 (채택)** — 커서 이후 데이터만 조회
   - 장점: 실시간 데이터 추가 시 중복/누락 없음. OFFSET 없이 인덱스 활용.
   - 단점: 특정 페이지로 직접 이동 불가. 복합 커서는 쿼리 복잡도 증가.

## 결과 (Consequences)

### 긍정
- 피드에 새 항목이 추가돼도 페이지 연속성 보장
- `id < :cursor` 조건이 인덱스를 활용해 성능 일정
- 앨범 피드 첫 페이지에서 정렬 단계가 제거되어 `64.4ms → 0.031ms`로 단축된 실측 결과가 있다 (10만 건 테스트 데이터 기준)
- 날짜 필터 조합도 range scan으로 처리되어 `51.9ms → 0.054ms`로 단축된 실측 결과가 있다

### 부정 / 트레이드오프
- "3페이지로 바로 이동" 같은 랜덤 접근 불가 → 현재 UI가 무한 스크롤 방식이므로 문제 없음
- 복합 커서 쿼리의 `OR` 조건이 인덱스 활용을 방해할 수 있음 → 커버링 인덱스로 보완
- ID 선조회 후 상세 조회를 수행하므로 쿼리가 2단계가 된다. 대신 컬렉션 fetch join과 페이징 충돌을 피한다.

## 후속 / 미결정
- 복합 커서 쿼리 실행 계획 주기적 확인 필요 (데이터 증가 후)
