#!/usr/bin/env python3
"""codex-review.sh 커밋 범위 검수(LLD-0093)와 push·PR 게이트 제거(LLD-0193) 회귀 테스트.

실행: python3 -m unittest discover -s scripts/harness -p 'test_*.py'

임시 저장소에 원본 체크아웃(develop)과 worktree 를 만들고, PATH 앞에 가짜 codex 를 둬
실제 Codex 호출 없이 판정(APPROVE·BLOCKER·실행 실패)을 흉내 낸다.
"""
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

HARNESS = Path(__file__).resolve().parent
REVIEW = HARNESS / "codex-review.sh"
GUARD = HARNESS / "pre-bash-guard.sh"

FAKE_CODEX = """#!/bin/bash
{ pwd; printf '%s\\n' "$@"; } > "$FAKE_CODEX_LOG"
if [[ -n "${FAKE_CODEX_FAIL:-}" ]]; then echo "network error"; exit 1; fi
echo "VERDICT: ${FAKE_CODEX_VERDICT:-APPROVE}"
"""

GIT_ENV = {"GIT_AUTHOR_NAME": "t", "GIT_AUTHOR_EMAIL": "t@t",
           "GIT_COMMITTER_NAME": "t", "GIT_COMMITTER_EMAIL": "t@t"}


def git(repo, *args):
    return subprocess.run(["git", "-C", str(repo), *args], capture_output=True,
                          text=True, check=True, env={**os.environ, **GIT_ENV}).stdout.strip()


def commit_file(repo, rel, text="x\n"):
    path = Path(repo) / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    git(repo, "add", rel)
    git(repo, "commit", "-q", "-m", f"add {rel}")


class CodexReviewTest(unittest.TestCase):

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp()).resolve()
        self.main = self.tmp / "repo"
        self.main.mkdir()
        git(self.main, "init", "-q", "-b", "develop")
        commit_file(self.main, "README.md")
        git(self.main, "update-ref", "refs/remotes/origin/develop", "HEAD")

        self.java_wt = self.tmp / "wt-java"
        git(self.main, "worktree", "add", "-q", "-b", "feature/java", str(self.java_wt))
        commit_file(self.java_wt, "backend/widyu-api/src/main/java/com/widyu/A.java", "class A {}\n")

        bin_dir = self.tmp / "bin"
        bin_dir.mkdir()
        (bin_dir / "codex").write_text(FAKE_CODEX)
        (bin_dir / "codex").chmod(0o755)
        self.codex_log = self.tmp / "codex.log"
        self.env = {**os.environ, **GIT_ENV, "PATH": f"{bin_dir}:{os.environ['PATH']}",
                    "FAKE_CODEX_LOG": str(self.codex_log)}

    def tearDown(self):
        shutil.rmtree(self.tmp, ignore_errors=True)

    # ── 헬퍼 ────────────────────────────────────────────────────────────
    def review(self, tree, *args, **env):
        return subprocess.run(["bash", str(REVIEW), *args], cwd=tree, capture_output=True,
                              text=True, env={**self.env, **env})

    def guard(self, command, cwd):
        payload = json.dumps({"session_id": "gate-test", "cwd": str(cwd),
                              "tool_input": {"command": command}})
        return subprocess.run(["bash", str(GUARD)], input=payload, capture_output=True,
                              text=True, env=self.env)

    def record(self, tree):
        git_dir = Path(git(tree, "rev-parse", "--absolute-git-dir"))
        path = git_dir / "widyu-codex-review" / git(tree, "rev-parse", "HEAD")
        return json.loads(path.read_text()) if path.exists() else None

    def audit(self, event):
        files = list((self.main / ".claude" / "audit").glob("audit-*.jsonl"))
        lines = [json.loads(line) for f in files for line in f.read_text().splitlines()]
        return [e for e in lines if e.get("event") == event]

    # ── AC1 ─────────────────────────────────────────────────────────────
    def test_ac1_branch_review_records_in_worktree_git_dir(self):
        p = self.review(self.java_wt, "--base", "origin/develop")
        self.assertEqual(p.returncode, 0, p.stdout + p.stderr)
        log = self.codex_log.read_text().splitlines()
        self.assertEqual(Path(log[0]).resolve(), self.java_wt)   # worktree 에서 실행
        self.assertIn("read-only", log)
        rec = self.record(self.java_wt)
        self.assertEqual(rec["verdict"], "APPROVE")
        self.assertEqual(rec["base"], git(self.main, "rev-parse", "develop"))
        self.assertFalse((self.main / ".git" / "widyu-codex-review").exists())
        events = self.audit("codex_branch_review")
        self.assertEqual([e["verdict"] for e in events], ["APPROVE"])
        self.assertEqual(Path(events[0]["tree"]).resolve(), self.java_wt)

    def test_ac1_no_arg_mode_unchanged(self):
        # 기존 호출은 미커밋 변경만 본다. 커밋된 Java 는 대상이 아니고 기록도 남기지 않는다.
        p = self.review(self.java_wt)
        self.assertEqual((p.returncode, p.stdout.strip()), (0, "NO_JAVA_CHANGES"))
        self.assertIsNone(self.record(self.java_wt))

    # ── LLD-0193: 게이트 제거 ───────────────────────────────────────────
    def test_push_and_pr_pass_without_review_record(self):
        for command, cwd in [
            (f"cd {self.java_wt} && git push -u origin feature/java", self.main),
            ("git push -u origin feature/java", self.java_wt),
            ("gh pr create --base develop --title t --body b", self.java_wt),
            (f"git -C {self.java_wt} push origin feature/java", self.main),
        ]:
            with self.subTest(command=command):
                p = self.guard(command, cwd)
                self.assertEqual((p.returncode, p.stderr), (0, ""))
        self.assertEqual(self.audit("codex_gate"), [])

    def test_force_push_still_blocked(self):
        p = self.guard("git push --force", self.java_wt)
        self.assertEqual(p.returncode, 2)
        self.assertIn("force push", p.stderr)


if __name__ == "__main__":
    unittest.main()
