# mody-server 하네스 엔지니어링 → WIDYU-server 적용 가이드

> 하네스 엔지니어링: **설계와 검증 장치를 먼저 고정하고, 그 기준에 맞춰 구현 품질을 관리한다.**

---

## 1. 방법론 전체 흐름

```
Team Discussion
      │
      ▼
   ADR                ← 기술 의사결정 + 대안 + 트레이드오프 기록
      │
      ▼
   LLD                ← API 스펙, 도메인 규칙, 예외 코드, 인수조건 설계
      │                  (이 문서가 PR 본문의 오라클)
      ▼
Test Scenario         ← LLD 인수조건 기반 엣지 케이스 명세
      │
      ▼
Code Generation       ← AI가 LLD + 테스트 시나리오 기준으로 구현
      │
      ▼
Human Review          ← diff + 테스트 결과를 LLD 기준으로 검토
      │
      ▼
PR / CI / Deploy      ← Codex 스킬로 일관된 형식 유지
```

**핵심 원칙**:
- 코드 작성 전 ADR과 LLD를 먼저 확정한다.
- LLD는 PR 본문의 오라클이다. LLD에 없는 내용은 PR 본문에 쓰지 않는다.
- 미결정 사항은 Open Questions에 남기고 추측으로 채우지 않는다.

---

## 2. mody-server 하네스 구성

| 계층 | 위치 | 역할 |
|------|------|------|
| 의사결정 | `docs/adr/ADR-XXXX-*.md` | 기술 선택 + 대안 + 리스크 기록 |
| 설계 | `docs/lld/LLD-XXXX-*.md` | 구현 단위 상세 설계 (PR 오라클) |
| 데이터 모델 | `docs/erd/ERD-XXXX-*.md` | 엔티티 기준 ERD — 코드와 항상 동기화 |
| API 명세 기준 | `docs/swagger/api-spec-guide.md` | Swagger 작성 표준 (공통 응답, 인증, 페이징 규칙) |
| 정책 확인 | `docs/policy/policy-checklist.md` | 구현 전 확인이 필요한 제품 정책 항목 |
| 템플릿 | `docs/templates/adr.md`, `lld.md` | ADR/LLD 작성 양식 |
| 인덱스 | `docs/adr/README.md`, `docs/lld/README.md` | 문서 목록 추적 |
| 프로젝트 지침 | `AGENTS.md` | Codex가 읽는 프로젝트 규칙 |
| 워크플로 자동화 | `.agents/skills/issue/`, `commit/`, `pr/` | 이슈·커밋·PR 양식 고정 |

### 각 문서의 역할과 관계

```
policy-checklist  ←→  ADR           (정책이 결정되면 ADR로 격상)
                        │
                        ▼
                       LLD           (API 스펙·도메인 규칙·인수조건)
                        │            ↕ 데이터 모델은 ERD와 교차 참조
                       ERD           (엔티티·컬럼·enum·인덱스 기준 문서)
                        │
                        ▼
               swagger/api-spec-guide  (API 문서화 표준 준수 여부 검토)
```

---

## 3. WIDYU 현재 상태 vs 필요한 것

### 로컬에 이미 있는 것

```
WIDYU-server/
├── CLAUDE.md                    # Claude Code 프로젝트 지침 (상세)
├── .claude/settings.json        # PostToolUse + Stop 훅 (자동 규칙 검사)
├── .mcp.json                    # widyu MCP 서버 (문서 검색, 테스트 실행)
└── scripts/harness/
    ├── on-file-edit.sh           # Java 파일 수정 시 자동 실행
    ├── validate-java-rules.sh    # 6개 Java 규칙 grep 검사
    ├── run-module-tests.sh       # 모듈별 테스트 실행
    └── on-stop.sh                # 작업 종료 시 체크리스트
```

> `.claude/`, `.mcp.json`, `apiDocs/`는 현재 `.gitignore` 대상이다. 팀 공통 하네스로 공유할 파일은 `docs/`, `AGENTS.md`, `.agents/skills/`, `scripts/harness/`처럼 커밋 가능한 위치에 둔다.

### 없는 것 (적용 대상)

```
WIDYU-server/
├── AGENTS.md                              ← Codex용 프로젝트 지침
├── docs/
│   ├── harness-engineering-adaptation.md  ← 이 적용 가이드의 공유용 위치
│   ├── adr/
│   │   ├── README.md                      ← ADR 인덱스
│   │   └── ADR-XXXX-*.md                  ← 기술 결정 기록
│   ├── lld/
│   │   ├── README.md                      ← LLD 인덱스
│   │   └── LLD-XXXX-*.md                  ← 상세 설계 (PR 오라클)
│   ├── erd/
│   │   └── ERD-0001-initial-domain.md     ← 현재 엔티티 기준 ERD (코드와 동기화)
│   ├── swagger/
│   │   └── api-spec-guide.md              ← Swagger 작성 표준
│   ├── policy/
│   │   └── policy-checklist.md            ← 구현 전 정책 확인 항목
│   └── templates/
│       ├── adr.md                         ← ADR 작성 템플릿
│       └── lld.md                         ← LLD 작성 템플릿
└── .agents/skills/
    ├── issue/SKILL.md                     ← 이슈 생성 워크플로
    ├── commit/SKILL.md                    ← 커밋 워크플로
    └── pr/SKILL.md                        ← PR 생성 워크플로
```

---

## 4. WIDYU 적용 시 차이점

| 항목 | mody-server | WIDYU-server |
|------|-------------|--------------|
| 모듈 구조 | 단일 모듈 | `widyu-api`, `widyu-domain` 멀티모듈 |
| 패키지 | `cmc.mody` | `com.widyu` |
| 문서 위치 | `docs/adr/`, `docs/lld/` | `docs/adr/`, `docs/lld/` (신규) |
| 기존 API 문서 | - | `apiDocs/` (유지, 별도 관리) |
| 기본 assignee | `msk226` | `dongkyun0713` |
| PR base 브랜치 | `main` | `develop` |
| commit scope 예시 | `auth`, `user` | `auth`, `album`, `health`, `pay`, `location` |
| LLD 인수조건 → 테스트 | JUnit 5 + H2 | JUnit 5 + Mockito + H2 (기존 패턴 유지) |

---

## 5. 파일 내용 — 템플릿

### 5-1. `docs/templates/adr.md`

```markdown
# ADR-XXXX: <결정 제목>

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Proposed / Accepted / Superseded by ADR-YYYY |
| 날짜 | YYYY-MM-DD |
| 관련 | LLD-XXXX, #N |

## 맥락 (Context)
<어떤 문제·제약 상황에서 이 결정을 하게 됐는가>

## 결정 (Decision)
<무엇을 하기로 했는가>

## 고려한 대안 (Considered Options)
1. **<대안 A>** — 장점 / 단점
2. **<대안 B>** — 장점 / 단점

## 결과 (Consequences)
### 긍정
-
### 부정 / 트레이드오프
-

## 후속 / 미결정
<이 결정 이후 남는 열린 질문>
```

---

### 5-2. `docs/templates/lld.md`

```markdown
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
```

---

### 5-3. `docs/adr/README.md`

```markdown
# ADR (Architecture Decision Records)

중요한 아키텍처/기술 의사결정을 기록한다. 새 ADR은 `../templates/adr.md`를 복사해 `ADR-XXXX-<slug>.md`로 만든다.

| 번호 | 제목 | 상태 | 날짜 |
| --- | --- | --- | --- |
| [ADR-0001](ADR-0001-multi-module-structure.md) | widyu-api + widyu-domain 멀티모듈 구조 채택 | Accepted | (날짜) |
```

---

### 5-4. `docs/lld/README.md`

```markdown
# LLD (Low-Level Design)

기능별 상세 설계 문서. **PR 본문의 오라클**로 사용된다. 새 LLD는 `../templates/lld.md`를 복사해 `LLD-XXXX-<slug>.md`로 만든다.

각 LLD에는 반드시 `## 미결정 사항(Open Questions)` 섹션을 둔다.

| 번호 | 제목 | 상태 | Issue |
| --- | --- | --- | --- |
```

---

## 6. 파일 내용 — ERD

### `docs/erd/ERD-0001-initial-domain.md` (골격)

```markdown
# ERD-0001: 초기 도메인 ERD

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | YYYY-MM-DD |
| 코드 동기화 | YYYY-MM-DD |
| 관련 | LLD-XXXX, ADR-XXXX |

## 목적

현재 코드의 JPA 엔티티를 기준으로 도메인 데이터 모델을 기록한다.
이 문서는 **실제 테이블·컬럼·enum·인덱스의 기준 문서**다.
엔티티 변경 시 코드와 함께 이 문서를 수정한다.

## 현재 모델링 규칙

- 공통 시간 필드는 `BaseTimeEntity`가 제공한다 (`created_at`, `updated_at`).
- PK는 각 엔티티가 `Long id` + `GenerationType.IDENTITY`로 선언한다.
- `status`는 모든 엔티티의 공통 필드가 아니다. 필요한 엔티티만 `Status` enum을 가진다.
- `Status` 값: `ACTIVE`, `INACTIVE`, `DELETED`, `PROCESSING`.
- soft delete는 전역 규칙이 아니다. `@SQLDelete`, `@Where` 적용 여부를 엔티티별로 기록한다.
- JPA 연관관계(`@ManyToOne`, `@OneToMany`, `@JoinColumn`)를 사용한다. ERD의 관계선은 현재 JPA 연관관계를 기준으로 그린다.
- enum은 `@Enumerated(EnumType.STRING)` 기준으로 문자열 저장한다.
- 엔티티는 `widyu-domain` 모듈에만 위치한다.

## Mermaid ERD

```mermaid
erDiagram
    (엔티티 추가)
```

## Enum 값

| enum | 값 |
| --- | --- |

## 주요 인덱스

| 테이블 | 인덱스명 | 컬럼 |
| --- | --- | --- |

## 도메인별 조회 기준

### (도메인명)
-

## 코드 동기화 메모

- 이 문서는 `backend/widyu-domain/src/main/java/com/widyu/**`의 현재 엔티티 기준이다.
- 새로운 엔티티, 컬럼, enum, 인덱스가 추가되면 이 문서를 함께 수정한다.
- MySQL ENUM 컬럼 추가 시 `ALTER TABLE` 명령을 PR 비고에 포함한다.
```

**WIDYU 적용 시 주의**: 엔티티는 `widyu-domain`에만 있다. `./gradlew compileJava`는 QueryDSL Q-클래스 재생성용이며, ERD 동기화는 별도 문서 수정으로 처리한다.

---

## 7. 파일 내용 — Swagger API Spec Guide

### `docs/swagger/api-spec-guide.md`

```markdown
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
```

---

## 8. 파일 내용 — Policy Checklist

### `docs/policy/policy-checklist.md`

```markdown
# 정책 확인 체크리스트

| 항목 | 값 |
| --- | --- |
| 상태 | Draft |
| 목적 | 개발 전 정책 확인 항목 정리 |
| 작성일 | YYYY-MM-DD |

이 문서는 API/엔티티 구현 전에 제품 정책 확인이 필요한 항목을 모은다.
결정된 내용은 ERD, LLD, ADR 중 성격에 맞는 문서에 반영한다.

## Auth / OAuth

- 1차 출시 소셜 로그인 범위: 카카오, Apple 외 추가 여부 확인.
- 탈퇴 후 동일 소셜 계정 재가입 시 기존 데이터 복구 여부.
- OAuth 필수 저장 값: provider_user_id 외 이메일/이름/프로필 이미지 저장 범위.

## 회원 / 가족

- 가족 그룹 최대 인원 수 확정.
- 시니어/보호자 역할 전환 가능 여부.
- 가족 탈퇴 후 공유 데이터(앨범 등) 처리 방식.

## 앨범 / 미디어

- 영상 최대 길이/용량 제한.
- 업로드 후 미연결 미디어(presigned URL만 발급하고 기록 미생성) 정리 정책.
- 앨범 삭제 시 미디어 물리 삭제 시점 (즉시 vs 배치).

## 포인트 / 결제

- 포인트 만료 정책 (만료 여부, 만료 기간).
- 환불 정책.
- 프리미엄 콘텐츠 접근 조건 (포인트 차감 vs 구독).

## 알림

- 알림 수신 설정 단위 (채널별 on/off 범위).
- 알림 보관 기간.
- 발송 실패 재시도 여부와 최대 횟수.

## 위치 / WebSocket

- 위치 데이터 보관 기간.
- Redis 저장 구조 (key 설계, TTL).
- 위치 공유 대상 범위 (가족 그룹 내 전체 vs 개별 선택).

## 삭제 / 보관

- soft delete 적용 대상 테이블 범위.
- unique 충돌 처리 (deleted_at 포함 unique vs 삭제 시 suffix 부여).
- 법적 보관이 필요한 데이터 여부.
```

---

## 9. 파일 내용 — AGENTS.md

```markdown
# Repository Guidelines

## Project Overview

WIDYU는 시니어(부모)와 보호자(자녀·가족)가 사진·영상을 공유하는 Spring Boot 플랫폼.
포인트 기반 프리미엄 콘텐츠, WebSocket 실시간 위치 추적, 건강 목표 관리 포함.

## 하네스 엔지니어링 워크플로

이 프로젝트는 하네스 엔지니어링 방식으로 개발한다.
공통 상세 규칙은 `CLAUDE.md`와 `backend/CLAUDE.md`를 기준으로 삼고, 이 문서는 Codex가 따라야 할 추가 워크플로만 정리한다.

```
Team Discussion → ADR → LLD → Test Scenario → Code Generation → Human Review → PR/CI/Deploy
```

- 기술적 선택은 ADR(`docs/adr/`)에 남긴다. 선택한 방법 + 대안 + 트레이드오프 + 후속 리스크 포함.
- ADR이 방향을 정하면 LLD(`docs/lld/`)에서 구현 단위를 설계한다.
- LLD의 인수조건이 테스트 시나리오가 된다. 이 시나리오 기준으로 코드를 생성한다.
- LLD는 PR 본문의 오라클이다. LLD에 없는 내용은 PR 본문에 쓰지 않는다.

## Multi-Module Structure

- `widyu-api`: 컨트롤러, 서비스, 리포지토리, 설정 (`com.widyu.WidyuApiApplication`)
- `widyu-domain`: JPA 엔티티, QueryDSL Q-클래스 (bootJar 비활성)

핵심 규칙: 엔티티 → `widyu-domain`, 리포지토리/서비스/컨트롤러 → `widyu-api`

## Build & Test Commands

- `./gradlew :backend:widyu-api:test`: API 모듈 테스트
- `./gradlew :backend:widyu-domain:test`: 도메인 모듈 테스트
- `./gradlew compileJava`: QueryDSL Q-클래스 재생성 (엔티티 변경 후 필수)
- `./gradlew bootRun --args='--spring.profiles.active=local'`: 로컬 실행

## Coding Conventions

- 삼항 연산자 금지 — if/else 또는 early return
- Service/Facade에서 `new XxxResponse(` 직접 생성 금지 → `from()`/`of()` 팩토리 사용
- Controller에서 Repository 직접 import 금지
- `@Async` 메서드에 `@Transactional` 필요
- Facade 패턴: 여러 서비스 조합 시 사용

## Testing

JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), H2 in-memory.
BDDMockito(`given/willReturn`), AAA(given/when/then) 패턴.
테스트 메서드명은 한글 언더스코어(예: `앨범_생성_시_FCM_전송`).
상태 검증을 우선한다. 외부 호출, 이벤트 발행, 삭제 같은 side effect는 `verify`로 검증할 수 있다.

## Agent-Specific Instructions

Codex 워크플로 스킬은 `.agents/skills`에 있다.
새 작업 시작 → issue 스킬, 코드 완료 → commit 스킬, 머지 준비 → pr 스킬.
Co-author 트레일러: `Co-Authored-By: Codex <codex@openai.com>`.

## Security

시크릿 커밋 금지.
환경변수: `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `FCM_CREDENTIALS_PATH`.
```

---

## 10. 파일 내용 — Codex 워크플로 스킬

### `.agents/skills/issue/SKILL.md`

```markdown
---
name: issue
description: WIDYU-server에서 GitHub Issue를 생성하고 필요하면 feature/{issue-number} 브랜치를 만든다.
---

# issue

WIDYU-server 작업을 시작하기 전에 GitHub Issue를 생성하고, 필요하면 `feature/{issue-number}` 브랜치를 만든다.

## 핵심 원칙

- 이슈 생성 전 LLD가 있으면 LLD 링크를 본문에 포함한다.
- 이슈 제목과 본문은 한글로 작성한다.
- 작업 범위가 크면 분리안을 제안한다.
- 미결정 사항은 `확인 필요`로 남긴다.
- 브랜치는 사용자 요청 시 또는 바로 작업할 때만 만든다.

## 절차

1. `gh issue list --state open --limit 30 --json number,title,labels`로 중복 확인.
2. 관련 LLD가 `docs/lld/`에 있으면 이슈 본문에 링크를 건다.
3. 아래 템플릿으로 본문 작성.
4. `gh issue create --title "<title>" --body-file <tmpfile>`.
5. 필요하면 `git switch -c feature/<issue-number>`.
6. 이슈 번호, URL, 브랜치명 보고.

## 이슈 본문 템플릿

```markdown
## 배경
<왜 필요한 작업인지>

## 관련 설계
- LLD: docs/lld/<있으면 경로>
- ADR: docs/adr/<있으면 경로>

## 작업 범위
- 변경 모듈: widyu-api / widyu-domain
- <구현/문서/테스트 단위>

## 완료 조건
- <LLD 인수조건 기반>

## 정책 확인 필요
- <있으면 작성, 없으면 "없음">
```

## 하지 말 것

- 중복 이슈 확인 없이 생성.
- 이슈 생성만 요청했는데 커밋/PR까지 진행.
- develop에서 바로 작업.
```

---

### `.agents/skills/commit/SKILL.md`

```markdown
---
name: commit
description: WIDYU-server에서 변경사항을 Conventional Commits 규칙으로 커밋한다.
---

# commit

WIDYU-server의 변경사항을 Conventional Commits 규칙에 따라 커밋한다.
타입·scope만 영문, 제목·본문은 한글.

## 핵심 원칙

- 사용자 요청 시에만 커밋한다.
- `.agents/`는 사용자가 명시하지 않으면 스테이징하지 않는다.
- `docs/adr`, `docs/lld`, `docs/erd`는 관련 구현 변경의 설계 산출물이면 함께 스테이징한다.
- 엔티티 변경이 포함되면 `./gradlew compileJava` 실행 여부를 먼저 확인한다.

## 절차

1. `git status --short --branch`로 브랜치와 변경 파일 확인.
2. `git diff`로 변경 내용 읽기.
3. 논리적 단위로 분리. 관련 없는 변경 제외.
4. 엔티티 변경 포함 시 Q-클래스 재생성 여부 확인.
5. 의도한 파일만 스테이징.
6. 관련 이슈가 있으면 `Refs #N` 또는 `Closes #N` 추가.

## 커밋 메시지 형식

```
<type>(<scope>): <한글 제목, 마침표 없음>

<한글 본문 — 왜 변경했는지>

Refs #<issue-number>
Co-Authored-By: Codex <codex@openai.com>
```

### type

`feat` / `fix` / `docs` / `refactor` / `perf` / `test` / `build` / `ci` / `chore`

### scope (WIDYU 도메인 기준)

| scope | 대상 |
|-------|------|
| `auth` | 인증/인가 |
| `album` | 앨범/사진/영상 |
| `health` | 건강 목표 |
| `pay` | 포인트/결제 |
| `location` | 실시간 위치 |
| `member` | 회원 정보 |
| `family` | 가족 그룹 |
| `notification` | 알림/FCM |
| `domain` | widyu-domain 모듈 |
| `ci` | GitHub Actions |

## 예시

```
feat(album): 앨범 공유 FCM 알림 전송 추가

LLD-0012 기준으로 @EventListener 방식으로 구현.
도메인 간 결합 제거.

Refs #42
Co-Authored-By: Codex <codex@openai.com>
```

## 하지 말 것

- 엔티티 변경 후 compileJava 생략.
- 미완성 테스트를 통과했다고 쓰기.
- 관련 없는 변경 묶기.
```

---

### `.agents/skills/pr/SKILL.md`

```markdown
---
name: pr
description: WIDYU-server에서 GitHub PR을 생성하거나 갱신한다.
---

# pr

WIDYU-server의 Pull Request를 생성하거나 갱신한다.
PR 본문은 LLD를 오라클로 삼아 작성한다. LLD에 없는 내용은 쓰지 않는다.

## 핵심 원칙

- base 브랜치는 `develop`.
- LLD가 있으면 반드시 링크하고 인수조건 기준으로 작성한다.
- Open Questions는 LLD의 미결정 사항을 그대로 옮긴다.
- 기본 Assignee: `dongkyun0713`.

## 사전 확인

- 현재 브랜치가 `develop` 또는 `main`이면 새 브랜치 필요 여부를 확인한다.
- 엔티티 변경이 있으면 `./gradlew compileJava` 여부 확인.
- MySQL ENUM 변경이 있으면 PR 비고에 ALTER TABLE 명령 명시.

## 절차

1. `git status --short --branch`, `git log --oneline -5` 확인.
2. 관련 이슈 `gh issue view <N>` 읽기.
3. 관련 LLD `docs/lld/LLD-XXXX.md` 읽기.
4. `git diff develop...HEAD --stat`으로 변경 범위 파악.
5. 아래 템플릿으로 본문 작성.
6. `gh pr create --base develop --head <branch> --title "<title>" --body-file <tmpfile> --assignee dongkyun0713`.
7. 필요한 라벨이 정해져 있으면 `gh pr edit <PR> --add-label "<label>"`로 추가한다.
8. `gh pr view <PR> --json assignees,labels`로 반영 확인.

## PR 본문 템플릿

```markdown
## 개요
<LLD 기준으로 이 PR이 해결하는 문제와 범위>

## 관련 문서
- LLD: docs/lld/<경로> (없으면 -)
- ADR: docs/adr/<경로> (없으면 -)

## 관련 이슈
Closes #<N>

## 변경 사항
- 변경 모듈: widyu-api / widyu-domain
<diff 기반 사실만 bullet>

## 테스트
- `./gradlew :backend:widyu-api:test`: 통과/실패
- `./gradlew :backend:widyu-domain:test`: 통과/실패 (domain 변경 시)

## 체크리스트
- [ ] 엔티티 변경 시 `./gradlew compileJava` 실행
- [ ] MySQL ENUM 변경 시 ALTER TABLE 명시
- [ ] `@Async` 메서드에 `@Transactional` 확인
- [ ] LLD 인수조건 전체 충족

## Open Questions (LLD 미결정 사항)
<LLD의 Open Questions를 그대로 옮김. 없으면 "없음">

## 비고
<배포 영향, 후속 작업>
```

## 하지 말 것

- LLD에 없는 설계를 본문에 서술하기.
- develop 대신 main으로 PR 올리기.
- 영어 제목으로 PR 만들기.
```

---

## 11. 듀얼 AI 협업 워크플로

### 역할 분담

```
┌─────────────────────────────────────────────────────┐
│                   Primary Flow                      │
│                                                     │
│  Claude Code ──→ 코드 작성 ──→ commit               │
│                                   │                 │
│                                   ▼                 │
│                  Codex review ──→ 검수 보고          │
│                                   │                 │
│                                   ▼                 │
│                          이슈 없으면 PR              │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│              Fallback (Claude 토큰 소진)             │
│                                                     │
│  Codex implement ──→ 코드 작성                       │
│         │                                           │
│         └──→ Codex review ──→ 자체 검수              │
│                                   │                 │
│                                   ▼                 │
│                          이슈 수정 후 PR             │
└─────────────────────────────────────────────────────┘
```

### 각 AI가 읽는 파일

| 파일 | Claude Code | Codex |
|------|-------------|-------|
| `CLAUDE.md` | ✅ 주요 지침 | ✅ 공통 상세 규칙 참조 |
| `backend/CLAUDE.md` | ✅ 도메인 상세 규칙 | ✅ 도메인 상세 규칙 참조 |
| `AGENTS.md` | ❌ | ✅ Codex 워크플로 지침 |
| `docs/lld/` | ✅ 구현 기준 | ✅ 구현 기준 + 검수 기준 |
| `docs/erd/` | ✅ 엔티티 참조 | ✅ 엔티티 참조 |
| `scripts/harness/validate-java-rules.sh` | ✅ 자동 실행 (훅) | ✅ review 스킬에서 직접 실행 |

### 검수 기준 (Codex가 확인하는 항목)

1. **LLD 인수조건** — LLD의 `## 7. 인수조건` 체크리스트 전체 충족 여부
2. **코딩 규칙** — `validate-java-rules.sh` 실행 결과 (삼항 연산자, DTO 팩토리, Repository 위치 등)
3. **모듈 배치** — 엔티티는 `widyu-domain`, 리포지토리/서비스는 `widyu-api`
4. **테스트** — LLD 인수조건에 대응하는 테스트 존재 여부
5. **엔티티 변경** — ERD 문서 동기화 여부, `./gradlew compileJava` 실행 여부
6. **Swagger** — `controller/docs/`의 `*Docs` 인터페이스 업데이트 여부

---

## 12. 파일 내용 — Codex 검수·구현 스킬

### `.agents/skills/review/SKILL.md`

```markdown
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
```

---

### `.agents/skills/implement/SKILL.md`

```markdown
---
name: implement
description: Claude Code를 사용할 수 없을 때 Codex가 LLD 기준으로 코드를 직접 구현한다. 사용자가 "Claude 없이 구현해줘", "Codex로 구현해줘", "직접 짜줘" 등을 요청할 때 사용.
---

# implement

Claude Code 없이 Codex가 직접 LLD를 기반으로 코드를 구현한다.
구현 완료 후 반드시 **review 스킬을 이어서 실행**해 자체 검수한다.

## 핵심 원칙

- LLD가 없으면 구현을 시작하지 않는다. LLD 작성을 먼저 요청한다.
- LLD의 `## 9. 미결정 사항`이 비어 있지 않으면 해당 항목을 사용자에게 확인하고 진행한다.
- `CLAUDE.md`, `backend/CLAUDE.md`, `AGENTS.md`의 규칙을 함께 따른다.
- 구현 단위는 LLD 하나 = PR 하나를 원칙으로 한다.

## 절차

1. 관련 LLD를 `docs/lld/`에서 읽는다.
2. 관련 ERD를 `docs/erd/`에서 읽는다.
3. LLD의 `## 9. 미결정 사항`을 확인하고 미결정 항목이 있으면 사용자에게 알린다.
4. LLD `## 2. 범위`에서 변경 모듈(widyu-api / widyu-domain)을 확인한다.
5. LLD `## 3. 인터페이스 / API`와 `## 4. 데이터 모델`을 기준으로 구현 계획을 세운다.
6. 계획을 사용자에게 먼저 보고하고 승인을 받는다.
7. 코드를 작성한다. 작성 중 `validate-java-rules.sh`를 참조해 규칙을 준수한다.
8. LLD `## 7. 인수조건`을 기반으로 테스트를 작성한다.
9. 구현 완료 후 **review 스킬을 실행**한다.
10. review 결과 `REQUEST_CHANGES`이면 수정 후 다시 review를 실행한다.
11. review 결과 `APPROVE`이면 commit 스킬로 커밋한다.

## 구현 규칙 (CLAUDE.md / backend/CLAUDE.md와 동일)

- 삼항 연산자 금지 — if/else 또는 early return
- Service/Facade에서 `new XxxResponse(` 금지 — `from()`/`of()` 팩토리 사용
- Controller에서 Repository 직접 import 금지
- 엔티티 → `widyu-domain`, 리포지토리/서비스/컨트롤러 → `widyu-api`
- `@Async` 메서드에 `@Transactional` 필수
- 여러 서비스 조합 시 Facade 패턴 사용
- 도메인 간 결합은 `@EventListener` 방식으로 분리

## 테스트 작성 규칙

- JUnit 5 + `@ExtendWith(MockitoExtension.class)`
- BDDMockito (`given/willReturn`) 사용
- 메서드명: 한글 언더스코어 (예: `앨범_업로드_시_FCM_전송`)
- `@DisplayName`: `<행위>하면/할 때 <결과>한다` 형식
- 상태 검증(`assertEquals`, `assertThat`) 우선
- 외부 호출, 이벤트 발행, 삭제 같은 side effect는 `verify` 허용

## 엔티티 변경 시 추가 작업

- `docs/erd/`의 ERD 문서를 수정한다.
- MySQL ENUM 변경이 있으면 `docs/lld/` 해당 LLD의 `## 8. 영향 범위`에 ALTER TABLE 명령을 기록한다.

## 하지 말 것

- LLD 없이 구현 시작.
- 미결정 사항을 임의로 결정하고 구현.
- review 스킬 없이 commit.
- LLD 범위 밖의 추가 기능 구현.
```

---

## 13. 생성 명령

```bash
# WIDYU-server 루트에서 실행
mkdir -p docs/adr docs/lld docs/erd docs/swagger docs/policy docs/templates
mkdir -p .agents/skills/issue .agents/skills/commit .agents/skills/pr
mkdir -p .agents/skills/review .agents/skills/implement
```

그 다음 각 절의 파일 내용을 해당 경로에 저장한다.
이 가이드는 현재 `apiDocs/`에 있는 초안이므로, 팀 공통 적용본은 `docs/harness-engineering-adaptation.md`로 옮겨 커밋한다.

---

## 14. 적용 후 전체 구성

```
WIDYU-server/
│
├── AGENTS.md                          # Codex용 프로젝트 지침 + 하네스 워크플로
├── CLAUDE.md                          # Claude Code용 프로젝트 지침 (기존 유지)
│
├── docs/
│   ├── harness-engineering-adaptation.md  # 하네스 적용 가이드
│   ├── adr/
│   │   ├── README.md                  # ADR 인덱스
│   │   └── ADR-XXXX-*.md              # 기술 의사결정 기록
│   ├── lld/
│   │   ├── README.md                  # LLD 인덱스
│   │   └── LLD-XXXX-*.md              # 상세 설계 (PR 오라클)
│   ├── erd/
│   │   └── ERD-0001-initial-domain.md # 엔티티 기준 ERD (코드와 동기화)
│   ├── swagger/
│   │   └── api-spec-guide.md          # Swagger 작성 표준
│   ├── policy/
│   │   └── policy-checklist.md        # 구현 전 정책 확인 항목
│   └── templates/
│       ├── adr.md                     # ADR 작성 양식
│       └── lld.md                     # LLD 작성 양식
│
├── .agents/
│   └── skills/
│       ├── issue/SKILL.md             # 이슈 생성 워크플로
│       ├── commit/SKILL.md            # 커밋 워크플로
│       ├── pr/SKILL.md                # PR 생성 워크플로
│       ├── review/SKILL.md            # Claude 결과물 검수 (Codex)
│       └── implement/SKILL.md         # Claude 대체 구현 (Codex 단독)
│
├── apiDocs/                           # 기존 API 참고 문서 (유지)
│
├── .claude/
│   └── settings.json                  # PostToolUse + Stop 훅 (기존)
├── .mcp.json                          # widyu MCP 서버 (기존)
└── scripts/harness/
    ├── on-file-edit.sh                 # Java 파일 수정 시 자동 규칙 검사 (기존)
    ├── validate-java-rules.sh          # 6개 Java 규칙 grep (기존)
    ├── run-module-tests.sh             # 모듈별 테스트 실행 (기존)
    └── on-stop.sh                      # 작업 종료 시 체크리스트 (기존)
```

**Claude Code (Primary)**: `CLAUDE.md` + `.claude/settings.json` + `scripts/harness/` + `.mcp.json`  
**Codex (Reviewer)**: `AGENTS.md` + `.agents/skills/review/`  
**Codex (Fallback)**: `AGENTS.md` + `.agents/skills/implement/` → `.agents/skills/review/`  
**두 도구 공통**: `docs/` 전체 — ADR, LLD, ERD, Swagger 가이드, Policy 체크리스트

### 문서별 갱신 시점

| 문서 | 갱신 시점 |
|------|-----------|
| `docs/policy/` | 팀 논의 중 미결정 정책 발생 시 추가 |
| `docs/adr/` | 정책 확정 또는 기술 선택 시 ADR로 격상 |
| `docs/lld/` | ADR 확정 후 구현 착수 전 작성, PR 머지 후 상태 `Approved` 유지 |
| `docs/erd/` | 엔티티·컬럼·enum·인덱스 변경 시 코드와 동시 수정 |
| `docs/swagger/` | API 문서화 표준 변경 시 (자주 변경되지 않음) |
