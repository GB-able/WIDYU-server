#!/usr/bin/env python3
"""pre-edit-branch-guard.sh 회귀 테스트.

실행: python3 scripts/harness/test-pre-edit-branch-guard.py

임시 git 저장소를 만들어 브랜치별 동작을 확인한다. 가드는 자기 파일 위치에서
ROOT_DIR 을 구하므로, 스크립트를 임시 저장소로 복사하면 그 저장소를 대상으로 돈다.
"""
import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

GUARD = Path(__file__).with_name("pre-edit-branch-guard.sh")


def git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args],
                          capture_output=True, text=True)


def make_repo(tmp):
    repo = Path(tmp) / "repo"
    (repo / "scripts" / "harness").mkdir(parents=True)
    (repo / "backend").mkdir()
    (repo / "apiDocs").mkdir()
    shutil.copy(GUARD, repo / "scripts" / "harness" / GUARD.name)
    git(repo, "init", "-q", "-b", "develop")
    git(repo, "-c", "user.email=t@t", "-c", "user.name=t",
        "commit", "-q", "--allow-empty", "-m", "init")
    return repo


def run(repo, path, session="sess1"):
    payload = json.dumps({"session_id": session,
                          "tool_input": {"file_path": str(path)}})
    return subprocess.run(["bash", str(repo / "scripts/harness" / GUARD.name)],
                          input=payload, capture_output=True, text=True)


def main():
    failed = []

    def check(label, got, expect, detail=""):
        if got != expect:
            failed.append((label, expect, got, detail))
            print(f"!!FAIL  exp={expect} got={got}  {label}")

    with tempfile.TemporaryDirectory() as tmp:
        repo = make_repo(tmp)
        java = repo / "backend" / "Foo.java"

        # 보호 브랜치는 무조건 차단
        check("develop 코드 수정 차단", run(repo, java).returncode, 2)
        git(repo, "switch", "-q", "-c", "main")
        check("main 코드 수정 차단", run(repo, java).returncode, 2)

        # 보호 브랜치는 ack 로도 못 뚫는다
        state = repo / ".claude" / "state"
        state.mkdir(parents=True, exist_ok=True)
        (state / "branch-ack-sess1-main-0.txt").write_text("x")
        check("main 은 ack 로도 못 뚫음", run(repo, java).returncode, 2)

        # feature 브랜치: 최초 1회 차단 → ack 후 통과
        git(repo, "switch", "-q", "-c", "feature/foo")
        p = run(repo, java)
        check("feature 최초 편집 차단", p.returncode, 2)

        m = re.search(r'> "([^"]*branch-ack-[^"]*\.txt)"', p.stderr)
        if not m:
            failed.append(("ack 경로 안내 파싱", "경로", "없음", p.stderr[:200]))
            print("!!FAIL  차단 메시지에서 ack 경로를 찾지 못함")
        else:
            ack_foo = Path(m.group(1))
            ack_foo.parent.mkdir(parents=True, exist_ok=True)
            ack_foo.write_text("이어서 작업")
            check("ack 후 통과", run(repo, java).returncode, 0)

            # REGRESSION: feature/foo 와 feature-foo 가 같은 ack 를 공유하면 안 된다.
            # 슬러그만 쓰면 tr 이 '/' 를 '-' 로 바꿔 두 브랜치가 한 파일명이 된다.
            git(repo, "switch", "-q", "-c", "feature-foo")
            check("REGRESSION 이름 충돌 브랜치는 재확인 요구",
                  run(repo, java).returncode, 2, f"ack={ack_foo.name}")

        # 제외 경로·저장소 밖은 통과
        git(repo, "switch", "-q", "feature/foo")
        check("apiDocs 통과", run(repo, repo / "apiDocs" / "x.md").returncode, 0)
        check(".claude 통과", run(repo, repo / ".claude" / "settings.json").returncode, 0)
        check("저장소 밖 통과", run(repo, Path(tmp) / "outside.java").returncode, 0)

        # fail-open: 파싱 실패는 통과시킨다 (보안 경계가 아니라 사고 방지 장치).
        # fail-closed 면 파싱이 깨지는 순간 편집이 전면 중단된다. ADR-0021 참조.
        p = subprocess.run(["bash", str(repo / "scripts/harness" / GUARD.name)],
                           input='{"broken', capture_output=True, text=True)
        check("깨진 JSON 은 fail-open", p.returncode, 0)

    total = 10
    print(f"\n총 {total}건 · 통과 {total - len(failed)} · 실패 {len(failed)}")
    for label, expect, got, detail in failed:
        print(f"  - {label}: exp={expect} got={got} {detail}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
