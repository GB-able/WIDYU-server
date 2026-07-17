"""aggregate-audit.py — 감사 로그에서 지표 5개를 집계해 마크다운 리포트를 출력한다.

사용:
    python3 scripts/harness/aggregate-audit.py [--months N] [--out path]
      --months N  : 최근 N개월 집계 (기본 1)
      --out path  : 출력 파일 경로 (기본 stdout)

지표 (harness-step8-measurement-plan.md §2.5):
  1. Codex REQUEST_CHANGES율 (+ APPROVE 도달률, 평균 라운드)
  2. 정적 규칙 위반율 (java_rule_check 이벤트 기반)
  3. 워크플로 이탈 수 (workflow_risk / operational_code_on_protected_branch 태그)
  4. 파이프라인 단계별 도달률 (pipeline_step 이벤트 — 미수집 시 N/A)
  5. 테스트 실행 횟수 (bash[test] 태그 — 성공 여부는 exit code 미수집으로 partial)

보조 지표:
  - dangerous 명령 수 (차단 대리 지표)
  - 세션 수 / 총 이벤트 수 / 집계 기간
"""

import argparse
import json
import sys
from collections import Counter, defaultdict
from datetime import datetime, timezone
from pathlib import Path

# ---------------------------------------------------------------------------
# 인수
# ---------------------------------------------------------------------------

ROOT = Path(__file__).resolve().parents[2]
AUDIT_DIR = ROOT / ".claude" / "audit"

ap = argparse.ArgumentParser()
ap.add_argument("--months", type=int, default=1)
ap.add_argument("--out", default=None)
args = ap.parse_args()

# ---------------------------------------------------------------------------
# 대상 파일 선택
# ---------------------------------------------------------------------------

now = datetime.now(timezone.utc)
target_months: list[str] = []
year, month = now.year, now.month
for _ in range(args.months):
    target_months.append(f"{year:04d}-{month:02d}")
    month -= 1
    if month == 0:
        month = 12
        year -= 1

audit_files = [AUDIT_DIR / f"audit-{m}.jsonl" for m in target_months]
audit_files = [f for f in audit_files if f.exists()]

if not audit_files:
    print(f"감사 로그 없음: {AUDIT_DIR}")
    sys.exit(0)

# ---------------------------------------------------------------------------
# 이벤트 로드
# ---------------------------------------------------------------------------

events: list[dict] = []
for path in audit_files:
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            events.append(json.loads(line))
        except json.JSONDecodeError:
            continue

if not events:
    print("이벤트 없음.")
    sys.exit(0)

# 기간
ts_list = [e["ts"] for e in events if "ts" in e]
period_start = min(ts_list)[:10]
period_end = max(ts_list)[:10]

# ---------------------------------------------------------------------------
# 집계
# ---------------------------------------------------------------------------

# 세션
sessions: set[str] = {e["session_id"] for e in events if e.get("session_id")}
total = len(events)

# 1. Codex RC율
codex_events = [e for e in events if e.get("event") == "codex_review"]
n_codex = len(codex_events)
n_approve = sum(1 for e in codex_events if e.get("verdict") == "APPROVE")
n_rc = sum(1 for e in codex_events if e.get("verdict") == "REQUEST_CHANGES")
n_failed = sum(1 for e in codex_events if e.get("verdict") == "FAILED")
rounds = [e.get("round", 0) for e in codex_events if e.get("verdict") == "REQUEST_CHANGES"]
avg_round = (sum(rounds) / len(rounds)) if rounds else 0
# APPROVE 도달률: 세션 중 최종 APPROVE 로 끝난 세션 수 (session_id 기준 마지막 verdict)
last_verdict: dict[str, str] = {}
for e in sorted(codex_events, key=lambda x: x.get("ts", "")):
    sid = e.get("session_id", "")
    if sid:
        last_verdict[sid] = e.get("verdict", "")
n_final_approve = sum(1 for v in last_verdict.values() if v == "APPROVE")
approve_reach_rate = (n_final_approve / len(last_verdict) * 100) if last_verdict else None

rc_rate = (n_rc / n_codex * 100) if n_codex else None

# 2. 정적 규칙 위반율
rule_events = [e for e in events if e.get("event") == "java_rule_check"]
n_rule = len(rule_events)
n_violation = sum(1 for e in rule_events if e.get("violations", 0) > 0)
violation_rate = (n_violation / n_rule * 100) if n_rule else None

# 3. 워크플로 이탈
def has_tag(e: dict, tag: str) -> bool:
    return tag in (e.get("tags") or [])

n_workflow_risk = sum(1 for e in events if has_tag(e, "workflow_risk"))
n_op_code = sum(1 for e in events if has_tag(e, "operational_code_on_protected_branch"))

# 4. 파이프라인 단계별 도달률
pipeline_events = [e for e in events if e.get("event") == "pipeline_step"]
step_counts: Counter = Counter(e.get("step", "unknown") for e in pipeline_events)

# 5. 테스트 실행
n_test_runs = sum(1 for e in events if e.get("event") == "bash" and has_tag(e, "test"))

# 보조: dangerous / git_write / build
n_dangerous = sum(1 for e in events if has_tag(e, "dangerous"))
n_git_write = sum(1 for e in events if has_tag(e, "git_write"))
n_build = sum(1 for e in events if e.get("event") == "bash" and has_tag(e, "build"))
n_session_id_missing = sum(1 for e in events if e.get("event") == "session_id_missing")


# ---------------------------------------------------------------------------
# 출력 헬퍼
# ---------------------------------------------------------------------------

def pct(value: float | None, decimals: int = 1) -> str:
    if value is None:
        return "N/A (이벤트 미수집)"
    return f"{value:.{decimals}f}%"

def na_if_none(value, fmt="{}", suffix="") -> str:
    if value is None:
        return "N/A"
    return fmt.format(value) + suffix

def na_if_zero(value, fmt="{}", suffix="") -> str:
    if value is None:
        return "N/A (이벤트 미수집)"
    return fmt.format(value) + suffix


# ---------------------------------------------------------------------------
# 마크다운 리포트
# ---------------------------------------------------------------------------

lines: list[str] = []
lines.append(f"# 하네스 감사 리포트")
lines.append(f"")
lines.append(f"- **집계 기간**: {period_start} ~ {period_end}")
lines.append(f"- **세션 수**: {len(sessions)}")
lines.append(f"- **총 이벤트**: {total}")
lines.append(f"- **생성**: {now.strftime('%Y-%m-%d %H:%M UTC')}")
lines.append(f"")

lines.append(f"## 핵심 지표 5")
lines.append(f"")

# 지표 1
lines.append(f"### 1. Codex 검수 품질")
if n_codex == 0:
    lines.append("N/A (codex_review 이벤트 미수집)")
else:
    lines.append(f"| 항목 | 값 |")
    lines.append(f"|---|---|")
    lines.append(f"| REQUEST_CHANGES율 | {pct(rc_rate)} ({n_rc}/{n_codex}) |")
    lines.append(f"| 최종 APPROVE 도달률 | {pct(approve_reach_rate)} |")
    lines.append(f"| RC 평균 라운드 | {avg_round:.1f} |")
    lines.append(f"| FAILED (Codex 실행 실패) | {n_failed}건 |")
    lines.append(f"")
    lines.append(f"> RC율 단독 해석 금지 — 리뷰 기준 강화일 수 있음. APPROVE 도달률·평균 라운드와 함께 판단.")
lines.append(f"")

# 지표 2
lines.append(f"### 2. 정적 규칙 위반율")
if violation_rate is None:
    lines.append(f"N/A — `java_rule_check` 이벤트 미수집 (on-file-edit.sh 연동 확인 필요)")
else:
    lines.append(f"| 항목 | 값 |")
    lines.append(f"|---|---|")
    lines.append(f"| 위반율 | {pct(violation_rate)} ({n_violation}/{n_rule}) |")
    lines.append(f"| 총 검사 횟수 | {n_rule} |")
    lines.append(f"")
    if violation_rate == 0.0 and n_rule >= 10:
        lines.append(f"> ⚠️ 위반율 0% 지속 — 정적 훅 축소 근거 검토 대상 (1세대 귀무결과 참조)")
lines.append(f"")

# 지표 3
lines.append(f"### 3. 워크플로 이탈")
lines.append(f"| 태그 | 건수 |")
lines.append(f"|---|---|")
lines.append(f"| workflow_risk (protected 브랜치 변경) | {n_workflow_risk} |")
lines.append(f"| operational_code_on_protected_branch | {n_op_code} |")
if n_workflow_risk > 0:
    lines.append(f"")
    lines.append(f"> ⚠️ {n_workflow_risk}건 이탈 감지 — 트랜스크립트 확인 → 규칙/훅 강화 검토")
lines.append(f"")

# 지표 4
lines.append(f"### 4. 파이프라인 단계별 도달률")
if not step_counts:
    lines.append(f"N/A — `pipeline_step` 이벤트 미수집 (Phase 2 pipeline 추적 미구현)")
else:
    steps_ordered = [
        "step1_issue", "step2_branch", "step3_test_first",
        "step4_implement", "step5_verify", "step6_review", "step7_pr",
    ]
    max_count = max(step_counts.values()) if step_counts else 1
    lines.append(f"| 단계 | 도달 횟수 | 도달률 |")
    lines.append(f"|---|---|---|")
    for step in steps_ordered:
        cnt = step_counts.get(step, 0)
        rate = cnt / max_count * 100 if max_count else 0
        lines.append(f"| {step} | {cnt} | {rate:.0f}% |")
lines.append(f"")

# 지표 5
lines.append(f"### 5. 테스트 실행")
lines.append(f"| 항목 | 값 |")
lines.append(f"|---|---|")
lines.append(f"| 테스트 실행 횟수 (`test` 태그 bash) | {n_test_runs} |")
lines.append(f"| 빌드 실행 횟수 (`build` 태그 bash) | {n_build} |")
lines.append(f"")
lines.append(f"> 성공/실패 여부는 PostToolUse hook 에 exit code 미포함으로 현재 미수집.")
lines.append(f"> build 로그 파싱 확장 시 통과율 추가 가능.")
lines.append(f"")

lines.append(f"## 보조 지표")
lines.append(f"")
lines.append(f"| 항목 | 값 |")
lines.append(f"|---|---|")
lines.append(f"| dangerous 명령 감지 | {n_dangerous}건 (예방된 사고 대리 지표) |")
lines.append(f"| git_write 이벤트 | {n_git_write}건 |")
lines.append(f"| session_id_missing | {n_session_id_missing}건 |")
lines.append(f"")

# 회고 힌트
lines.append(f"## 회고 힌트")
lines.append(f"")
hints: list[str] = []
if rc_rate is not None and rc_rate > 30:
    hints.append(f"- RC율 {rc_rate:.0f}% — 해당 도메인 CLAUDE.md/스킬 보강 검토")
if violation_rate is not None and violation_rate == 0.0 and n_rule >= 10:
    hints.append(f"- 정적 위반율 0% — on-file-edit 훅 축소 여부 검토 (비용 절감)")
if n_workflow_risk > 0:
    hints.append(f"- workflow_risk {n_workflow_risk}건 — 트랜스크립트 확인 후 훅 경고→차단 승격 여부 결정")
if n_dangerous > 0:
    hints.append(f"- dangerous {n_dangerous}건 — 차단 정책(HITL) 검토")
if n_session_id_missing > 0:
    hints.append(f"- session_id_missing {n_session_id_missing}건 — 훅 stdin 스키마 변경 여부 확인")
if not hints:
    hints.append("- 특이 항목 없음 — 현 정책 유지")
lines.extend(hints)
lines.append(f"")
lines.append(f"---")
lines.append(f"_이 리포트는 `scripts/harness/aggregate-audit.py` 로 생성됩니다._")

report = "\n".join(lines)

# ---------------------------------------------------------------------------
# 출력
# ---------------------------------------------------------------------------

if args.out:
    Path(args.out).write_text(report, encoding="utf-8")
    print(f"리포트 저장: {args.out}")
else:
    print(report)
