# ADR-0018: Claude Code 샌드박스를 도입하지 않는다

> Architecture Decision Record. 하나의 중요한 의사결정과 그 이유를 기록한다.

| 항목 | 값 |
| --- | --- |
| 상태 | Accepted |
| 날짜 | 2026-08-20 |
| 관련 | Issue #488, PR #485 |

## 맥락 (Context)

PR #485에서 `.claude/settings.json`에 비밀 파일 deny 규칙을 넣었다.
루트 `.env`에 실제 시크릿 51개(`MYSQL_PASSWORD`, `JWT_*_SECRET` 3종, `COOLSMS_API_SECRET`,
`OAUTH_NAVER_CLIENT_SECRET` 등)가 있는데 어떤 스코프에도 deny가 없었기 때문이다.

리뷰에서 "`Read` deny는 Bash 하위 프로세스에 적용되지 않으므로 `sandbox`가 필요하다"는
지적이 나왔고, 그 검증이 이 ADR의 출발점이다.

### deny 규칙의 실제 차단 범위 (실측)

더미 파일에 deny 규칙을 걸고 명령별로 측정했다. 실제 `.env`는 사용하지 않았다.

| 명령 | 결과 |
| --- | --- |
| `cat FILE`, `cat -n FILE` | 차단 |
| `head -1 FILE`, `grep PAT FILE` | 차단 |
| `tail -1 FILE`, `sed -n 1p FILE`, `awk '{print}' FILE` | **통과** |
| `read -r L < FILE` | **통과** |
| `python3 -c "open(FILE).read()"`, `node -e "readFileSync(FILE)"` | **통과** |
| `( cat .env )` — 괄호 서브셸 | **통과** |

플래그를 붙여도 매처가 인자를 이해하므로 `cat -n`은 막힌다.
그러나 **인식 목록에 없는 리더(`tail`·`sed`·`awk`)와 인터프리터 내부 읽기는 통과**한다.
결정적으로, 명시적으로 deny한 `cat .env`조차 **괄호 서브셸로 감싸면 통과**한다.
명령 문자열 파서를 우회하는 방법이 남아 있는 한 deny는 보안 경계가 될 수 없다.

즉 리뷰 지적은 타당하다. deny는 실수 방지용 가드다.

## 결정 (Decision)

**샌드박스를 도입하지 않는다.** deny 기반 가드를 현행대로 유지한다.

샌드박스는 위 문제를 실제로 해결하지만(아래 실측), **테스트 스위트를 무력화**한다.
`bash scripts/harness/run-module-tests.sh`는 CLAUDE.md 작업 순서 4단계이고 매 작업에서 실행된다.
그 결과가 전부 거짓 실패로 바뀌는 비용이 얻는 보안 이득보다 크다.

## 검증한 사실 (Claude Code v2.1.237, macOS)

### 샌드박스가 해결하는 것

`sandbox.enabled: true` + `filesystem.denyRead`를 걸면 위 표의 **통과 6건이 전부
OS 레벨에서 차단**된다(`Operation not permitted`). deny로 막을 수 없던 영역이다.

아래는 예외 설정으로 정상 동작시켰다.

| 항목 | 필요한 설정 |
| --- | --- |
| `./gradlew compileJava` | `network.allowLocalBinding: true` (파일 락 소켓), `filesystem.allowWrite: ["~/.gradle"]`, `env.JAVA_HOME` |
| `docker` | `network.allowUnixSockets: ["~/.docker/run/docker.sock"]` |
| `gh` TLS | `enableWeakerNetworkIsolation: true` (`com.apple.trustd.agent` 허용) |
| git, curl | 추가 설정 없이 동작 |
| 하네스 훅 전체 | **영향 없음** — 훅은 Claude Code가 직접 실행하며 Bash 도구 샌드박스 밖에서 돈다 |

훅이 샌드박스 밖이라는 점은 일부러 확인했다. 삼항 연산자가 든 Java 파일을 만들자
`on-file-edit.sh`가 정상적으로 규칙1 위반을 잡고 차단했다.

### 샌드박스가 깨뜨리는 것

1. **테스트 425건 중 423건 실패 (치명적)**
   `Could not initialize plugin: interface org.mockito.plugins.MockMaker` 405건 외 컨텍스트 로딩 실패.
   Mockito inline mock maker가 실행 중인 JVM에 에이전트를 자기-어태치하는데
   샌드박스 프로세스 정책이 이를 막는다.
   `filesystem.allowWrite`에 `/tmp`·`/var/folders`를 추가해도 해결되지 않는다(파일 쓰기 문제가 아니다).
   같은 명령을 샌드박스 밖에서 실행하면 `BUILD SUCCESSFUL`이다(A/B 확인).

2. **`gh` 인증 불가**
   TLS는 `enableWeakerNetworkIsolation`으로 해결되지만, 토큰이 macOS 키체인에 있어
   `HTTP 401`이 남는다. 키체인 접근을 열면 `.env` 하나를 막으려다 **모든 자격증명을 노출**하므로
   해법으로 채택할 수 없다.

3. **`mktemp` 실패가 조용한 통과를 만든다**
   `TMPDIR`이 샌드박스 쓰기 범위 밖이라 `mktemp`가 실패한다.
   `validate-java-rules.sh`를 Bash 도구로 직접 실행하면 임시 파일 생성에 실패한 뒤
   **위반 0건 · exit 0**으로 끝난다. 훅 경로는 영향받지 않지만, 수동 실행 결과가 사람을 오도한다.

4. **`excludedCommands`로 우회할 수 없다**
   `sandbox.excludedCommands`에 `gradlew`·`./gradlew`·`gradle`을 넣어도 gradle은 계속 샌드박스 안에서 돌았다.
   프로젝트 설정에서 이 키가 반영되지 않는 것으로 보인다(세션 재시작 조건은 확인하지 못했다).

5. **켠 뒤에는 Bash로 되돌릴 수 없다**
   샌드박스가 `.claude/settings.json` 쓰기를 막는다. 끄려면 Edit 도구나 에디터로 직접 고쳐야 한다.

6. gradle의 FSEvents 파일 감시가 비활성화된다(성능 영향만 있다).

## 고려한 대안 (Considered Options)

1. **샌드박스 전면 도입** — 보안은 해결되나 테스트가 죽는다. 기각.
2. **`excludedCommands`로 gradle만 제외** — 동작하지 않았다. 동작하더라도 gradle 태스크를
   통해 임의 코드가 샌드박스 밖에서 실행되므로 격리 의미가 희석된다.
3. **deny 목록에 `tail`·`sed`·`awk`·`python3`를 추가** — 괄호 서브셸 우회가 남으므로
   차단력은 거의 오르지 않고 "막았다"는 착각만 커진다. 기각.
4. **현행 deny 유지 + 한계 명시** — 채택.

## 결과 (Consequences)

### 긍정

- 테스트·빌드·`gh`·docker 워크플로가 그대로 유지된다.
- deny는 `cat`·`head`·`grep` 같은 **가장 흔한 실수 경로**를 여전히 막는다.
- 도입 시 필요한 예외 설정과 남는 문제를 실측으로 확보해, 재검토 시 처음부터 다시 조사하지 않아도 된다.

### 부정 / 트레이드오프

- **`.env` 읽기를 완전히 막지 못한다.** `tail`·`sed`·`awk`·인터프리터·괄호 서브셸로 우회된다.
  deny는 보안 경계가 아니라 실수 방지용 가드라는 전제를 팀이 공유해야 한다.
- 시크릿 보호의 실질적 방어선은 여전히 `.gitignore`와 유출 시 키 교체다.

## 후속 / 미결정

- Mockito가 자기-어태치를 요구하지 않게 되거나(예: mock-maker-subclass 전환),
  Claude Code가 프로세스 정책을 완화하면 재검토한다.
- `excludedCommands`가 프로젝트 설정에서 동작하는지 세션 재시작 후 확인한다.
  동작한다면 "gradle만 제외 + 나머지 샌드박스"가 다시 후보가 된다.
- `gh` 토큰을 키체인 대신 파일·환경변수로 두는 방식은 토큰 평문 노출과 맞바꾸는 것이라
  현재로서는 권하지 않는다.
