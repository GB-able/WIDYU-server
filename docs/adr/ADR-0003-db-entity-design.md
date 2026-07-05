# ADR-0003: DB/엔티티 설계 기준

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-07-05 |
| 관련 | ADR-0001, ERD-0001 |

## 맥락 (Context)

프로젝트 초기에 여러 DB/엔티티 설계 기준을 팀 내에서 묵시적으로 합의했다.
이 규칙들이 문서화되지 않으면 새 엔티티 추가 시 기준이 흔들리고, 코드 리뷰에서 불필요한 논쟁이 발생한다.

## 결정 (Decision)

**PK 전략**
- 모든 JPA 엔티티: `Long id` + `GenerationType.IDENTITY`
- Redis 엔티티(`@RedisHash`): 도메인에 따라 `Long` 또는 `String` 타입

**시간 필드**
- `BaseTimeEntity`가 `created_at`, `updated_at`을 공통 제공
- 타임존이 중요한 필드(결제 등)는 `ZonedDateTime` 별도 선언

**Enum 저장**
- `@Enumerated(EnumType.STRING)` 기준으로 문자열 저장
- MySQL `ddl-auto: update`는 기존 ENUM 컬럼에 새 값을 추가하지 않음 → 새 값 추가 시 수동 `ALTER TABLE` 필수

**연관관계**
- JPA `@ManyToOne`, `@OneToMany`, `@JoinColumn` 사용
- 물리적 FK 제약조건은 사용하지 않음 (데이터 삽입 유연성 확보, 대규모 데이터 성능)
- Lazy Loading 기본 (`FetchType.LAZY`), N+1은 QueryDSL 또는 fetch join으로 해결

**Soft Delete**
- 전역 규칙이 아님. 필요한 엔티티만 `@SQLDelete` + `@Where` 적용
- 적용 여부는 ERD-0001 문서에 엔티티별로 기록

**모듈 배치**
- JPA 엔티티 → `widyu-domain`
- Redis 엔티티(`@RedisHash`) → `widyu-domain`
- 리포지토리·서비스·컨트롤러 → `widyu-api`

## 고려한 대안 (Considered Options)

1. **물리적 FK 사용** — DB 레벨 무결성 보장
   - 단점: 대용량 데이터 삽입 성능 저하, 테스트 데이터 삽입 복잡도 증가, JPA와 이중 관리
   - 채택하지 않음

2. **UUID PK** — 분산 환경 ID 충돌 없음
   - 단점: 인덱스 성능 저하, 현재 단일 DB 환경에서 불필요
   - 채택하지 않음

3. **EnumType.ORDINAL** — 저장 공간 작음
   - 단점: enum 순서 변경 시 기존 데이터 오염
   - 채택하지 않음

## 결과 (Consequences)

### 긍정
- 신규 엔티티 추가 시 기준이 명확해 일관된 코드 생성
- `validate-java-rules.sh`가 `@Entity` 위치를 자동 검사

### 부정 / 트레이드오프
- 물리적 FK 없음 → 잘못된 참조 데이터가 DB에 들어갈 수 있음. 애플리케이션 레벨 검증이 방어선
- MySQL ENUM 추가 시 수동 `ALTER TABLE` 필요. PR 본문 비고에 명시 의무화

## 후속 / 미결정
- soft delete 적용 대상 테이블 확정은 `docs/policy/policy-checklist.md` → 정책 결정 후 각 엔티티에 적용
