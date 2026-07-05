# ADR (Architecture Decision Records)

중요한 아키텍처/기술 의사결정을 기록한다. 새 ADR은 `../templates/adr.md`를 복사해 `ADR-XXXX-<slug>.md`로 만든다.

**ADR을 쓰는 기준**: "이 결정을 바꾸면 많은 파일이 바뀐다" 또는 "여러 기능에 반복 적용되는 규칙"이면 ADR 작성.
구현하다가 "이걸 어떻게 할까?" 고민이 30분 이상 걸리면 ADR 후보. 단순히 어떻게 만드냐만 결정하면 LLD에 포함.

| 번호 | 제목 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [ADR-0001](ADR-0001-multi-module-structure.md) | widyu-api + widyu-domain 멀티모듈 구조 채택 | Accepted | 2025-08-01 |
| [ADR-0002](ADR-0002-auth-jwt-family-access.md) | 인증/인가 전략 — JWT + @ValidateFamilyAccess AOP | Accepted | 2026-07-05 |
| [ADR-0003](ADR-0003-db-entity-design.md) | DB/엔티티 설계 기준 (PK, Enum, FK, soft delete) | Accepted | 2026-07-05 |
| [ADR-0004](ADR-0004-media-upload-strategy.md) | 미디어 업로드 전략 — 서버 직접 S3 업로드 + @Async | Accepted | 2026-07-05 |
| [ADR-0005](ADR-0005-cursor-pagination.md) | 커서 기반 페이징 전략 | Accepted | 2026-07-05 |
