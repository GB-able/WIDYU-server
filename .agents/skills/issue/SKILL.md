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
