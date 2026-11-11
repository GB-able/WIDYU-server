# LLD-0132: 운영자가 어르신 한 분의 위치 열람 기록을 기간으로 CSV로 내려받기

> Low-Level Design. 이 문서는 해당 기능 구현과 PR 본문의 **오라클(ground truth)** 이다.

| 항목 | 값 |
| --- | --- |
| 상태 | Review |
| Issue | #995 |
| 관련 ADR | [ADR-0036](../adr/ADR-0036-privacy-records-consent-access-logs.md) 결정 3·5 |
| 작성자 | Claude |
| 작성일 | 2026-11-10 |

## 1. 목적 / 배경

어르신이 고객센터에 「내 위치를 누가 언제 봤는지」를 요청하면 운영자가 본인 확인 뒤 이메일로 자료를 보낸다(위치정보 이용약관 제6조, 위치정보법 제16조②). 열람 기록은 `location_access_log`(LLD-0056)에 쌓이지만 운영자가 한 어르신의 기록을 기간으로 꺼낼 길이 없어 DB를 직접 조회해야 했다. DB 직접 조회는 관리자 접속기록(LLD-0057)에도 남지 않는다. 관리자 API 하나로 CSV 파일을 내려받게 한다.

## 2. 범위

### In scope
- 변경 모듈: widyu-api, widyu-domain(오류 코드 1개)
- 관리자 전용 동기 API 하나. 응답은 CSV 파일 하나.
- `LocationAccessLogRepository`에 기간 목록 조회 메서드 하나.

### Out of scope
- 이메일 발송. 운영자가 받은 파일을 직접 보낸다.
- 관리자 웹 화면 버튼.
- 열람 기록 통보 경로(`LocationAccessLogService`·스케줄러·`location-access` 설정). 이 기능은 읽기만 한다.
- 열람 기록의 보관 상한·삭제(별도 설계).
- 업무 행위 기록(`admin_audit_log`)에 내보내기 행 남기기. 이유는 5절 4번.

## 3. 인터페이스 / API

```http
GET /api/v1/admin/members/{memberId}/location-access-exports?from=2026-08-01T00:00:00&to=2026-11-01T00:00:00
Authorization: Bearer <관리자 토큰>

200 OK
Content-Type: text/csv;charset=UTF-8
Content-Disposition: attachment; filename="location_access_member28_20260801T0000-20261101T0000.csv"
```

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `memberId` | O | 어르신 회원 ID(열람당한 쪽) |
| `from`, `to` | O | **한국 시간(Asia/Seoul)** ISO 날짜시간. `from < to`, 양 끝 포함, 최대 1년(`to <= from + 1년`) |

`accessed_at`·`notified_at`은 서버 기본 시간대로 저장한 `LocalDateTime`이고 dev·prod는 `-Duser.timezone=Asia/Seoul`이다. 그래서 `from`·`to`를 바꾸지 않고 그대로 비교하고, epoch ms는 저장값을 한국 시간으로 읽어 환산한다.

CSV는 UTF-8 BOM으로 시작한다. 받는 사람이 엑셀로 열 때 한글이 깨지지 않게 하기 위해서다. 행은 열람 시각 오름차순(같으면 기록 ID 순)이고, 기록이 없으면 머리글만 있다.

| 칸(머리글) | 값 |
| --- | --- |
| `열람 시각(한국 시간)` | `yyyy-MM-dd HH:mm:ss` |
| `열람 시각(epoch ms)` | UTC epoch 밀리초 |
| `보호자 회원 ID` | `viewer_member_id` |
| `보호자 이름` | 현재 `Member.name`. 회원 행이 없으면 빈 칸 |
| `경로 코드` | `REST_LAST` 등 enum 이름 |
| `경로` | 아래 표의 설명 |
| `통보 여부` | `예` / `아니오` (`notified_at` 유무) |
| `통보 시각(한국 시간)` | 통보하지 않았으면 빈 칸 |

| 경로 코드 | 경로 |
| --- | --- |
| `REST_LAST` | 최근 위치 조회 |
| `REST_TRAIL` | 최근 15분 이동 경로 조회 |
| `REST_FAMILY` | 가족 어르신 목록에서 위치 조회 |
| `WS_SUBSCRIBE` | 실시간 위치 보기 시작 |
| `HOME_OUTING` | 홈 화면 외출 여부 확인 |

**좌표는 없다.** 테이블에 좌표가 없고(LLD-0056), 넣지 않는다(LLD-0029).

**보호자 이름을 넣는다.** 받는 사람이 기록의 주인인 어르신 본인이고, 보호자는 같은 가족이다. 앱의 본인 조회(`GET /api/v1/location/access-logs/mine`)도 `viewerName`을 같은 이유로 보여 준다(LLD-0056 5.5). 회원 ID만으로는 어르신이 「누가」 봤는지 알 수 없어 법이 요구하는 확인 자료가 되지 못한다. 이름은 본인 조회와 같은 현재 `Member.name`이다.

**수식 방지.** 보호자 이름은 회원이 입력한 값이다. 문자열이 `=`·`+`·`-`·`@`·탭·CR로 시작하면 앞에 `'`를 붙이고, `,`·`"`·줄바꿈이 있으면 큰따옴표로 감싼다(LLD-0108 `SensorWindowCsv`와 같은 규칙). 센서 내보내기 파일은 다른 PR이 고치고 있어 공유하지 않고, 같은 규칙을 위치 열람 패키지의 `LocationAccessLogCsv`에 둔다.

## 4. 데이터 모델

스키마 변경 없음.

- `LocationAccessLogRepository.findBySeniorMemberIdAndAccessedAtBetweenOrderByAccessedAtAscIdAsc(seniorMemberId, from, to)` — 기존 인덱스 `(senior_member_id, accessed_at)`.
- 응답 파일은 `location/access/dto/response/LocationAccessExportFile(fileName, bytes)` record로 컨트롤러에 넘긴다.
- 오류 코드 `LOCATION_ACCESS_EXPORT_RANGE_INVALID`(400, `LOCATION_4001`). 위치 쪽에 기간 오류 코드가 없어 새로 둔다. `LOCATION_4000`은 위치 원본 형식 오류라 뜻이 다르다.

## 5. 처리 흐름

1. `SecurityConfig`의 `/api/v1/admin/**` `hasRole("ADMIN")`로 관리자만 들어온다.
2. `LocationAccessExportService.export`(`@Transactional(readOnly = true)`)가 기간을 검사하고(`from < to`, `to <= from + 1년`) 회원 존재를 확인한다.
3. 기간 안의 행을 한 번에 읽고, 보호자 이름은 중복을 뺀 보호자 ID로 `findAllById` 한 번에 읽는다(행마다 조회하면 N+1).
4. **기록.** `AdminAccessLogInterceptor`(LLD-0057)가 관리자·시각·경로·`target_member_id`(경로 변수 `memberId`)·응답 상태·쿼리 이름(`from&to`)을 접속기록에 자동으로 남긴다. 업무 행위 기록(`admin_audit_log`)은 남기지 않는다. ADR-0036 결정 5가 두 기록을 「어느 화면을 열었나」(접속기록)와 「무엇을 바꿨나」(감사 로그)로 나눴고, `AdminAction`은 로그인·상태 변경·회차 열고 닫기·실증 참여 변경처럼 상태를 바꾸는 행위만 담는다. 같은 성격의 읽기 반출인 센서 내보내기(LLD-0108)와 관리자 동의 이력 조회도 접속기록에만 기댄다. 감수하는 점: 접속기록은 쿼리 값을 남기지 않아 「어느 기간을 뽑았나」는 기록되지 않는다(LLD-0057의 정책, LLD-0108과 같음).
5. CSV를 메모리에서 만들어 바이트로 돌려준다. 응답을 쓰기 전에 다 만들기 때문에 실패하면 정상 오류 응답이 나간다.
6. 로그에는 회원 ID와 행 수만 남긴다. 이름·좌표는 남기지 않는다.

## 6. 예외 / 에러 처리

| 상황 | 응답 |
| --- | --- |
| `from >= to` 또는 1년 초과 | 400 `LOCATION_4001` 열람 기록 내보내기 기간이 올바르지 않습니다. |
| `from`·`to` 누락·형식 오류 | 400 (공통 요청 검증) |
| 회원 없음 | 404 `MEMBER_4041` |
| 관리자 아님 | 403 (보안 설정) |

## 7. 인수조건 (Acceptance Criteria)

- [x] 회원 ID와 한국 시간 `from`·`to`를 주면 그 기간(양 끝 포함)의 그 어르신 열람 기록만 시간순 CSV로 내려온다.
- [x] 각 행에 열람 시각(한국 시간·epoch ms), 보호자 회원 ID·이름, 경로 코드·설명, 통보 여부·시각이 들어가고 좌표 칸이 없다.
- [x] 통보하지 않은 행은 `아니오`와 빈 통보 시각, 회원 행이 없는 보호자는 빈 이름이다.
- [x] 보호자 이름이 `=`·`-` 등으로 시작하면 `'`가 붙고, 쉼표·따옴표가 있으면 감싸진다.
- [x] 파일은 UTF-8 BOM으로 시작하고 응답은 `text/csv` 첨부 파일이다.
- [x] `from >= to`나 1년 초과는 `LOCATION_4001`, 없는 회원은 `MEMBER_4041`이며 저장소를 읽지 않는다.
- [x] 관리자 경로라 접속기록에 관리자·대상 회원이 남는다(기존 인터셉터, `memberId` 경로 변수).
- [x] 로그에 이름·좌표가 없고 회원 ID와 건수만 있다.
- [x] Swagger에 파라미터·CSV 칸·오류가 반영된다.
- [x] `bash scripts/harness/verify.sh`가 통과한다.

## 8. 영향 범위 / 마이그레이션

신규 API·조회 메서드·오류 코드만 추가한다. DDL·ENUM 변경 없음. 경로 변수 `memberId`를 받으므로 `ControllerFamilyAccessTest`의 서비스 계층 검증 목록에 관리자 경로로 등록한다(`/api/v1/admin/**`는 `hasRole("ADMIN")`이 막는다). 통보 경로는 바뀌지 않는다. 한 어르신의 1년 기록은 많아야 수만 행이라 메모리에 한 번에 담는다.

## 9. 미결정 사항 (Open Questions)

없음.
